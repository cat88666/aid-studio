package com.sakura.sakura.controller;

import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.model.definition.OfficialImageCapabilityTemplateRegistry;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 官方图片模型能力补齐预览入口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/sakura/sakuramodel")
public class OfficialImageCapabilityTemplateController extends BaseController {
    private final OfficialImageCapabilityTemplateRegistry templates;

    @GetMapping("/image-capability-template/{modelId}")
    @PreAuthorize("@ss.hasPermi('sakura:sakuramodel:edit')")
    @Operation(summary = "预览官方图片能力补齐", description = "保留已有参数、协议与价格；新增能力默认停用，仅预览不保存。")
    public AjaxResult preview(@PathVariable Long modelId) {
        return success(templates.preview(modelId));
    }
}
