package com.sakura.model.probe.impl;

import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.model.probe.ProbeResult;

/**
 * 无安全元数据端点时仅验证网关可达性的探测模板。
 */
public abstract class AbstractGatewayReachabilityProbe extends AbstractReadOnlyProbe {

    @Override
    protected String resolvePath(SakuraAiModel model, SakuraAiProvider provider) {
        return "/";
    }

    @Override
    protected ProbeResult interpret(SakuraAiModel model, SakuraAiProvider provider, ProbeHttpResponse response) {
        if (ProbeHttpSupport.isHttpSuccess(response)) {
            ProbeResult result = ProbeResult.ok("仅网关可达");
            result.setDetail("未验证密钥或模型");
            return result;
        }
        if (ProbeHttpSupport.isHttpRedirect(response)) {
            return ProbeHttpSupport.gatewayOnlyForRedirect(response);
        }
        return ProbeHttpSupport.unexpected(response);
    }
}
