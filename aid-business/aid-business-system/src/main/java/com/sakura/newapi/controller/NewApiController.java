package com.sakura.newapi.controller;

import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.newapi.NewApiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** New API 普通用户授权管理；响应始终不包含上游凭证。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/sakura/newapi/{providerId}")
public class NewApiController extends BaseController {
    private final NewApiService service;

    @GetMapping("/account")
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit')")
    public AjaxResult account(@PathVariable Long providerId) {
        return success(service.account(providerId));
    }

    @GetMapping("/tokens")
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit')")
    public AjaxResult tokens(@PathVariable Long providerId, @RequestParam(defaultValue = "1") int page) {
        return success(service.tokens(providerId, page));
    }

    @PostMapping("/bind-token")
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit')")
    public AjaxResult bind(@PathVariable Long providerId, @Valid @RequestBody BindToken request) {
        service.bindToken(providerId, request.tokenId(), request.group());
        return success();
    }

    @GetMapping("/catalog")
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit') and @ss.hasPermi('sakura:sakuramodel:list')")
    public AjaxResult catalog(@PathVariable Long providerId, @RequestParam String group) {
        return success(service.catalog(providerId, group));
    }

    public record BindToken(@Positive long tokenId, @NotBlank String group) { }

    @PostMapping("/create-token")
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit')")
    public AjaxResult create(@PathVariable Long providerId, @Valid @RequestBody CreateToken request) {
        service.createAndBindToken(providerId, request.group());
        return success();
    }

    public record CreateToken(@NotBlank String group) { }

    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermi('sakura:sakuraprovider:edit') and @ss.hasPermi('sakura:sakuramodel:add')")
    public AjaxResult importModels(@PathVariable Long providerId, @Valid @RequestBody ImportModels request) {
        return success(service.importModels(providerId, request.group(), request.selections(), getUsername()));
    }

    public record ImportModels(@NotBlank String group, java.util.List<NewApiService.Selection> selections) { }
}
