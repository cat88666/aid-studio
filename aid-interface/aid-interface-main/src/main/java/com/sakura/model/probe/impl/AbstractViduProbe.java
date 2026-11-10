package com.sakura.model.probe.impl;

import java.util.Objects;

import com.alibaba.fastjson2.JSONObject;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.media.constants.ViduConstants;
import com.sakura.media.provider.ViduStatusMapper;
import com.sakura.model.probe.ProbeResult;
import com.sakura.common.utils.ProviderEndpointUtils;

import cn.hutool.core.util.StrUtil;

/**
 * Vidu 不存在任务只读查询探测模板。
 */
public abstract class AbstractViduProbe extends AbstractReadOnlyProbe {

    @Override
    protected String resolvePath(SakuraAiModel model, SakuraAiProvider provider) {
        return ProviderEndpointUtils.normalizeTaskQueryTemplate(provider.getTaskQuerySuffix())
                .replace("%s", ProbeHttpSupport.randomNumericTaskId());
    }

    @Override
    protected void applyAuth(cn.hutool.http.HttpRequest request, SakuraAiProvider provider) {
        ProbeHttpSupport.applyAuth(request, provider, "Authorization", ViduConstants.AUTH_TOKEN_PREFIX);
    }

    @Override
    protected ProbeResult interpret(SakuraAiModel model, SakuraAiProvider provider, ProbeHttpResponse response) {
        if (ProbeBusinessResponseSupport.isKnownTaskMissing(response.body())) {
            return ProbeResult.ok("鉴权查询正常");
        }
        JSONObject root = ProbeHttpSupport.parseObject(response.body());
        if (ProbeHttpSupport.isHttpSuccess(response) && Objects.nonNull(root)
                && ViduStatusMapper.isKnownState(resolveStatus(root))) {
            return ProbeResult.ok("鉴权查询正常");
        }
        return ProbeHttpSupport.unexpected(response);
    }

    private String resolveStatus(JSONObject root) {
        String status = root.getString("status");
        if (StrUtil.isBlank(status)) {
            status = root.getString("state");
        }
        if (StrUtil.isBlank(status)) {
            status = root.getString("task_status");
        }
        JSONObject data = root.getJSONObject("data");
        if (StrUtil.isBlank(status) && Objects.nonNull(data)) {
            status = data.getString("status");
        }
        if (StrUtil.isBlank(status) && Objects.nonNull(data)) {
            status = data.getString("state");
        }
        return status;
    }
}
