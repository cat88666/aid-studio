package com.sakura.sakura.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.enums.BusinessType;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.domain.dto.SakuraAiProviderStatusRequest;
import com.sakura.sakura.controller.support.AiConfigJsonValidator;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.sakura.media.service.ConcurrencyConfigValidator;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * AI大模型服务商(官方渠道)配置Controller
 *
 * @author 视觉AID
 */
@Slf4j
@RestController
@RequestMapping("/sakura/sakuraprovider")
public class SakuraAiProviderController extends BaseController
{
    @Autowired
    private ISakuraAiProviderService sakuraAiProviderService;

    /** 并发上限层级校验器（全局 ≥ 供应商 ≥ 模型） */
    @Autowired
    private ConcurrencyConfigValidator concurrencyConfigValidator;

    /**
     * 查询AI大模型服务商(官方渠道)配置列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraAiProvider sakuraAiProvider)
    {
        startPage();
        List<SakuraAiProvider> list = sakuraAiProviderService.selectSakuraAiProviderList(sakuraAiProvider);
        return getDataTable(list);
    }

    /**
     * 导出AI大模型服务商(官方渠道)配置列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:export')")
    @Log(title = "AI大模型服务商(官方渠道)配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraAiProvider sakuraAiProvider)
    {
        List<SakuraAiProvider> list = sakuraAiProviderService.selectSakuraAiProviderList(sakuraAiProvider);
        ExcelUtil<SakuraAiProvider> util = new ExcelUtil<SakuraAiProvider>(SakuraAiProvider.class);
        util.exportExcel(response, list, "AI大模型服务商(官方渠道)配置数据");
    }

    /**
     * 获取AI大模型服务商(官方渠道)配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraAiProviderService.selectSakuraAiProviderById(id));
    }

    /**
     * 新增AI大模型服务商(官方渠道)配置
     * 记录审计日志，便于追溯上游渠道的创建者。
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:add')")
    @Log(title = "AI大模型服务商(官方渠道)配置", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraAiProvider sakuraAiProvider)
    {
        // 写入前统一校验所有 JSON 列（schedule_strategy_json / extra_*），
        // 避免 UUID / 普通字符串误填到 JSON 字段污染 resolveStrategy 等下游链路
        AiConfigJsonValidator.validate(sakuraAiProvider);
        // 并发上限层级校验：供应商上限不得超过全局上限
        concurrencyConfigValidator.validateProviderSave(sakuraAiProvider);
        log.warn("[AUDIT-PROVIDER] 新增 provider, operator={}, providerCode={}, baseUrl={}",
                getUsername(),
                sakuraAiProvider == null ? null : sakuraAiProvider.getProviderCode(),
                sakuraAiProvider == null ? null : sakuraAiProvider.getBaseUrl());
        return toAjax(sakuraAiProviderService.insertSakuraAiProvider(sakuraAiProvider));
    }

    /**
     * 修改AI大模型服务商(官方渠道)配置
     * 记录审计日志，标记 baseUrl / 密钥是否变更（仅标记是否变更，不打印密钥明文）。
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit')")
    @Log(title = "AI大模型服务商(官方渠道)配置", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraAiProvider sakuraAiProvider)
    {
        // 写入前统一校验所有 JSON 列，避免脏数据污染 DB
        AiConfigJsonValidator.validate(sakuraAiProvider);
        // 并发上限层级校验：不得超过全局上限，也不得小于其下模型已配上限
        concurrencyConfigValidator.validateProviderSave(sakuraAiProvider);
        if (sakuraAiProvider != null && sakuraAiProvider.getId() != null) {
            SakuraAiProvider old = sakuraAiProviderService.selectSakuraAiProviderById(sakuraAiProvider.getId());
            if (old != null) {
                boolean apiKeyChanged = sakuraAiProvider.getApiKey() != null
                        && !sakuraAiProvider.getApiKey().isEmpty()
                        && !sakuraAiProvider.getApiKey().equals(old.getApiKey());
                boolean apiSecretChanged = sakuraAiProvider.getApiSecret() != null
                        && !sakuraAiProvider.getApiSecret().isEmpty()
                        && !sakuraAiProvider.getApiSecret().equals(old.getApiSecret());
                boolean baseUrlChanged = sakuraAiProvider.getBaseUrl() != null
                        && !java.util.Objects.equals(sakuraAiProvider.getBaseUrl(), old.getBaseUrl());
                log.warn("[AUDIT-PROVIDER] 修改 provider, operator={}, id={}, code={}, "
                                + "baseUrlChanged={} ({} -> {}), apiKeyChanged={}, apiSecretChanged={}",
                        getUsername(), old.getId(), old.getProviderCode(),
                        baseUrlChanged, old.getBaseUrl(), sakuraAiProvider.getBaseUrl(),
                        apiKeyChanged, apiSecretChanged);
            }
        }
        return toAjax(sakuraAiProviderService.updateSakuraAiProvider(sakuraAiProvider));
    }

    /**
     * 修改AI大模型服务商启停状态。
     * 该接口只更新状态与审计字段，不触发完整配置编辑的网关地址校验。
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit')")
    @Log(title = "AI大模型服务商(官方渠道)配置", businessType = BusinessType.UPDATE)
    @PutMapping("/status")
    public AjaxResult updateStatus(@Valid @RequestBody SakuraAiProviderStatusRequest request)
    {
        log.info("[AUDIT-PROVIDER] 修改 provider 状态, operator={}, id={}, status={}",
                getUsername(), request.getId(), request.getStatus());
        return toAjax(sakuraAiProviderService.updateSakuraAiProviderStatus(
                request.getId(), request.getStatus(), getUsername()));
    }

    /**
     * 删除AI大模型服务商(官方渠道)配置
     * 记录审计日志，便于追溯 provider 下线。
     */
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:remove')")
    @Log(title = "AI大模型服务商(官方渠道)配置", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        log.warn("[AUDIT-PROVIDER] 删除 provider, operator={}, ids={}",
                getUsername(), java.util.Arrays.toString(ids));
        return toAjax(sakuraAiProviderService.deleteSakuraAiProviderByIds(ids));
    }
}
