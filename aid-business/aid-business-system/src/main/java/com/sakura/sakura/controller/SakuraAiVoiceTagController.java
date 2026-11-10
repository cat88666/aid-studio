package com.sakura.sakura.controller;

import java.util.List;
import com.sakura.sakura.domain.SakuraAiVoiceTag;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.core.page.TableDataInfo;
import com.sakura.common.enums.BusinessType;
import com.sakura.voice.dto.VoiceTagListRequest;
import com.sakura.voice.dto.VoiceTagUpsertRequest;
import com.sakura.voice.service.IVoiceTagBusinessService;
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

/**
 * 后台音色标签字典 Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/voice-tag")
public class SakuraAiVoiceTagController extends BaseController
{
    @Resource
    private IVoiceTagBusinessService voiceTagBusinessService;

    /**
     * 分页查询音色标签列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-tag:list')")
    @PostMapping("/list")
    public TableDataInfo list(@RequestBody VoiceTagListRequest request)
    {
        startPage();
        List<SakuraAiVoiceTag> list = voiceTagBusinessService.listVoiceTags(request);
        return getDataTable(list);
    }

    /**
     * 获取音色标签详情
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-tag:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(voiceTagBusinessService.getVoiceTagDetail(id));
    }

    /**
     * 新增音色标签
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-tag:add')")
    @Log(title = "音色标签", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    public AjaxResult add(@Valid @RequestBody VoiceTagUpsertRequest request)
    {
        Long id = voiceTagBusinessService.createVoiceTag(request);
        return success(id);
    }

    /**
     * 修改音色标签
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-tag:edit')")
    @Log(title = "音色标签", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody VoiceTagUpsertRequest request)
    {
        voiceTagBusinessService.updateVoiceTag(request);
        return success();
    }

    /**
     * 批量软删除音色标签
     */
    @PreAuthorize("@ss.hasPermi('sakura:voice-tag:remove')")
    @Log(title = "音色标签", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        voiceTagBusinessService.deleteVoiceTags(ids);
        return success();
    }
}
