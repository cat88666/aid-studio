package com.sakura.config.asr.controller;

import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.enums.BusinessType;
import com.sakura.compose.dto.TencentAsrConfigUpdateCommand;
import com.sakura.compose.dto.TencentAsrTestRequest;
import com.sakura.compose.service.TencentAsrAdminConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 腾讯云语音识别配置后台接口。 */
@RestController
@RequestMapping("/sakuraconfig/tencent-asr")
@RequiredArgsConstructor
public class TencentAsrConfigController extends BaseController {

    private final TencentAsrAdminConfigService configService;

    /**
     * 读取腾讯云语音识别配置，SecretId 与 SecretKey 已脱敏。
     *
     * @return 配置内容，放在 data 字段中
     */
    @PreAuthorize("@ss.hasPermi('sakuraconfig:sakuraconfig:edit')")
    @GetMapping("/config")
    public AjaxResult getConfig() {
        return AjaxResult.success(configService.getMaskedConfig());
    }

    /**
     * 整组保存腾讯云语音识别配置，并立即刷新运行时配置。
     * 密钥包含 **** 或留空时保留原值。
     *
     * @param request 配置保存请求
     * @return 保存成功提示
     */
    @PreAuthorize("@ss.hasPermi('sakuraconfig:sakuraconfig:edit')")
    @Log(title = "腾讯云语音识别配置", businessType = BusinessType.UPDATE)
    @PostMapping("/config")
    public AjaxResult saveConfig(@RequestBody TencentAsrConfigUpdateCommand request) {
        configService.save(request);
        return AjaxResult.success("保存成功");
    }

    /**
     * 上传临时视频，使用已保存的腾讯云配置等待识别完成并返回最终文本与时间戳分段。
     * 测试允许在自动字幕总开关关闭时执行，临时视频会在测试结束后从对象存储删除。
     *
     * @param request 测试视频表单
     * @return 最终识别结果，放在 data 字段中
     */
    @PreAuthorize("@ss.hasPermi('sakuraconfig:sakuraconfig:edit')")
    @Log(title = "腾讯云语音识别测试", businessType = BusinessType.OTHER,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult test(@Valid @ModelAttribute TencentAsrTestRequest request) {
        return AjaxResult.success(configService.test(request));
    }
}
