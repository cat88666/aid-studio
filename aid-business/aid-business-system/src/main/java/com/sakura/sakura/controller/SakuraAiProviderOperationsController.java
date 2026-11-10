package com.sakura.sakura.controller;

import com.sakura.sakura.domain.dto.ProviderUpstreamTaskQuery;
import com.sakura.sakura.service.IProviderUpstreamOperationsService;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台供应商动态扩展能力（余额、上游任务）。 */
@RestController
@RequestMapping("/sakura/sakuraprovider/{providerId}/operations")
@RequiredArgsConstructor
@Tag(name = "供应商上游运维", description = "按供应商能力查询余额和上游任务")
public class SakuraAiProviderOperationsController extends BaseController {

    private final IProviderUpstreamOperationsService operationsService;

    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:query')")
    @GetMapping("/capabilities")
    @Operation(summary = "查询上游运维能力", description = "返回当前供应商声明支持的余额和任务查询能力")
    public AjaxResult capabilities(@PathVariable Long providerId) {
        return success(operationsService.capabilities(providerId));
    }

    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:query')")
    @GetMapping("/balance")
    @Operation(summary = "查询上游余额", description = "按时间范围查询供应商资源包余额")
    public AjaxResult balance(@PathVariable Long providerId,
                              @RequestParam(required = false) Long startTime,
                              @RequestParam(required = false) Long endTime,
                              @RequestParam(required = false) String resourcePackName) {
        return success(operationsService.balance(providerId, startTime, endTime, resourcePackName));
    }

    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:list')")
    @PostMapping("/tasks")
    @Operation(summary = "查询上游任务", description = "按精确任务编号或游标筛选查询供应商任务")
    public AjaxResult tasks(@PathVariable Long providerId,
                            @RequestBody(required = false) ProviderUpstreamTaskQuery query) {
        return success(operationsService.tasks(providerId, query));
    }
}
