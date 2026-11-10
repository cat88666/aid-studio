package com.sakura.common.sakura.real.controller;

import com.sakura.common.sakura.crypto.annotation.CryptoIgnore;
import com.sakura.common.sakura.real.core.RealAuthTemplateFactory;
import com.sakura.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 实名认证配置Controller。
 *
 * @author 视觉AID
 */
@CryptoIgnore
@RestController
@RequestMapping("/realAuth/config")
public class RealAuthConfigController {

    @Autowired
    private RealAuthTemplateFactory realAuthTemplateFactory;

    /**
     * 刷新实名认证配置
     * 在配置页面修改后点击"刷新配置"按钮调用
     */
    @PreAuthorize("@ss.hasPermi('realAuth:config:refresh')")
    @PostMapping("/refresh")
    public AjaxResult refresh() {
        realAuthTemplateFactory.refresh();
        return AjaxResult.success();
    }

    /**
     * 获取当前生效的实名认证配置
     * 在配置页面展示当前使用的参数
     */
    @PreAuthorize("@ss.hasPermi('realAuth:config:query')")
    @GetMapping("/current")
    public AjaxResult getCurrentConfig() {
        Map<String, String> config = realAuthTemplateFactory.getCurrentConfig();
        return AjaxResult.success(config);
    }
}
