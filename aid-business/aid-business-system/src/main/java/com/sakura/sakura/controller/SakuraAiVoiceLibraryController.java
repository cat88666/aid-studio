package com.sakura.sakura.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.enums.BusinessType;
import com.sakura.voice.dto.VoiceLibraryListRequest;
import com.sakura.voice.dto.VoiceLibraryStatusRequest;
import com.sakura.voice.dto.VoiceLibraryUpsertRequest;
import com.sakura.voice.service.IVoiceLibraryBusinessService;
import com.sakura.voice.vo.VoiceLibraryVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * 后台 AI 音色库 Controller
 *
 * @author 视觉AID
 */
@Slf4j
@RestController
@RequestMapping("/sakura/voice-library")
public class SakuraAiVoiceLibraryController extends BaseController
{
    @Resource
    private IVoiceLibraryBusinessService voiceLibraryBusinessService;

    /**
     * 音色分页列表（后台）
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:list')")
    @PostMapping("/list")
    public AjaxResult list(@RequestBody VoiceLibraryListRequest request)
    {
        IPage<VoiceLibraryVO> page = voiceLibraryBusinessService.listForAdmin(request);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("data", page.getRecords());
        ajax.put("total", page.getTotal());
        ajax.put("pageNum", page.getCurrent());
        ajax.put("pageSize", page.getSize());
        return ajax;
    }

    /**
     * 音色详情
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(voiceLibraryBusinessService.getDetail(id));
    }

    /**
     * 新增音色
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:add')")
    @Log(title = "音色库", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    public AjaxResult add(@Valid @RequestBody VoiceLibraryUpsertRequest request)
    {
        Long id = voiceLibraryBusinessService.createVoice(request);
        return success(id);
    }

    /**
     * 修改音色
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:edit')")
    @Log(title = "音色库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody VoiceLibraryUpsertRequest request)
    {
        voiceLibraryBusinessService.updateVoice(request);
        return success();
    }

    /**
     * 启用 / 停用切换
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:edit')")
    @Log(title = "音色库", businessType = BusinessType.UPDATE)
    @PutMapping("/status")
    public AjaxResult updateStatus(@Valid @RequestBody VoiceLibraryStatusRequest request)
    {
        voiceLibraryBusinessService.updateVoiceStatus(request);
        return success();
    }

    /**
     * 批量软删除音色
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:remove')")
    @Log(title = "音色库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        voiceLibraryBusinessService.deleteVoices(ids);
        return success();
    }

    /**
     * 按模型远程同步音色。
     * 当前只对 MiniMax 生效（从模型提交路径派生只读音色查询路径）；豆包不提供远程音色列表，
     * 传入豆包模型会提示 `暂不支持`。同步流程：拉远程 → 本地 diff → upsert + 软删不存在的音色。
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:edit')")
    @Log(title = "音色库", businessType = BusinessType.UPDATE)
    @PostMapping("/sync/{modelId}")
    public AjaxResult syncByModel(@PathVariable("modelId") Long modelId)
    {
        try
        {
            com.sakura.voice.vo.VoiceSyncResultVO result = voiceSyncService.syncByModel(
                    modelId, com.sakura.common.utils.SecurityUtils.getUsername());
            return success(result);
        }
        catch (RuntimeException e)
        {
            log.error("音色同步失败: modelId={}, err={}", modelId, e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    @Resource
    private com.sakura.voice.service.IVoiceSyncService voiceSyncService;

    /**
     * 拉取 MiniMax 远程音色列表（不入库，仅供前端展示选择）。
     * 返回远程全量音色 + 本地已入库的 voiceCode 集合，前端据此渲染"已选中"状态。
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:edit')")
    @PostMapping("/sync/fetch-remote/{modelId}")
    public AjaxResult fetchRemoteVoices(@PathVariable("modelId") Long modelId)
    {
        try
        {
            return success(voiceSyncService.fetchRemoteWithLocalStatus(modelId));
        }
        catch (RuntimeException e)
        {
            log.error("拉取远程音色失败: modelId={}, err={}", modelId, e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 按用户选择同步音色（多选入库 + 取消选择的软删）。
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:edit')")
    @Log(title = "音色库", businessType = BusinessType.UPDATE)
    @PostMapping("/sync/apply")
    public AjaxResult syncApplySelected(@org.springframework.web.bind.annotation.RequestBody
                                         com.sakura.voice.dto.VoiceSyncApplyRequest request)
    {
        try
        {
            com.sakura.voice.vo.VoiceSyncResultVO result = voiceSyncService.applySelectedSync(
                    request, com.sakura.common.utils.SecurityUtils.getUsername());
            return success(result);
        }
        catch (RuntimeException e)
        {
            log.error("音色选择同步失败: err={}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 清除过期音色（offline_time ≤ NOW() 的活跃音色批量软删）。
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-library:edit')")
    @Log(title = "音色库", businessType = BusinessType.DELETE)
    @PostMapping("/clean-expired")
    public AjaxResult cleanExpired()
    {
        try
        {
            int count = voiceSyncService.cleanExpiredVoices(
                    com.sakura.common.utils.SecurityUtils.getUsername());
            return success(count);
        }
        catch (RuntimeException e)
        {
            log.error("清除过期音色失败: err={}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }
}
