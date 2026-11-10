package com.sakura.model.probe.impl;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.model.probe.ProbeResult;
import com.sakura.common.utils.ProviderEndpointUtils;

import cn.hutool.core.util.StrUtil;

/**
 * DashScope 视频任务只读查询探测。
 */
@Component
public class DashscopeVideoProbe extends AbstractReadOnlyProbe {

    private static final Set<String> KNOWN_TASK_STATUSES = Set.of(
            "PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELED", "UNKNOWN");

    @Override
    public String protocol() {
        return "dashscope-video";
    }

    @Override
    protected String resolvePath(SakuraAiModel model, SakuraAiProvider provider) {
        return ProviderEndpointUtils.normalizeTaskQueryTemplate(provider.getTaskQuerySuffix())
                .replace("%s", ProbeHttpSupport.randomProbeId());
    }

    @Override
    protected ProbeResult interpret(SakuraAiModel model, SakuraAiProvider provider, ProbeHttpResponse response) {
        if (ProbeBusinessResponseSupport.isKnownTaskMissing(response.body())) {
            return ProbeResult.ok("鉴权查询正常");
        }
        JSONObject root = ProbeHttpSupport.parseObject(response.body());
        if (ProbeHttpSupport.isHttpSuccess(response) && Objects.nonNull(root)
                && isKnownTaskStatus(resolveTaskStatus(root))) {
            return ProbeResult.ok("鉴权查询正常");
        }
        return ProbeHttpSupport.unexpected(response);
    }

    private String resolveTaskStatus(JSONObject root) {
        JSONObject output = root.getJSONObject("output");
        if (Objects.nonNull(output) && StrUtil.isNotBlank(output.getString("task_status"))) {
            return output.getString("task_status");
        }
        if (StrUtil.isNotBlank(root.getString("task_status"))) {
            return root.getString("task_status");
        }
        JSONObject data = root.getJSONObject("data");
        return Objects.isNull(data) ? null : data.getString("task_status");
    }

    private boolean isKnownTaskStatus(String status) {
        return StrUtil.isNotBlank(status)
                && KNOWN_TASK_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }
}
