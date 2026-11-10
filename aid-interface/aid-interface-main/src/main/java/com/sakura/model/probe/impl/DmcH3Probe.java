package com.sakura.model.probe.impl;

import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.common.utils.ProviderEndpointUtils;
import com.sakura.model.probe.ProbeResult;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

/** DMC 仅通过单任务查询进行非生成探测。 */
@Component
public class DmcH3Probe extends AbstractReadOnlyProbe {
    @Override public String protocol() { return "dmc-h3-video"; }
    @Override public String providerCode() { return "dmc"; }

    @Override
    protected String resolvePath(SakuraAiModel model, SakuraAiProvider provider) {
        return ProviderEndpointUtils.normalizeTaskQueryTemplate(provider.getTaskQuerySuffix())
                .replace("%s", ProbeHttpSupport.randomProbeId());
    }

    @Override
    protected ProbeResult interpret(SakuraAiModel model, SakuraAiProvider provider, ProbeHttpResponse response) {
        JSONObject root = ProbeHttpSupport.parseObject(response.body());
        if (ProbeHttpSupport.isHttpSuccess(response) && root != null && root.getJSONObject("task") != null) {
            ProbeResult result = ProbeResult.ok("只读查询可用");
            result.setDetail("未创建视频，当前模型生成能力需单独测试");
            return result;
        }
        if (response.status() == 404) {
            ProbeResult result = ProbeResult.ok("仅网关可达");
            result.setDetail("随机任务不存在，无法由响应确认密钥或模型权限");
            return result;
        }
        return ProbeHttpSupport.unexpected(response);
    }
}
