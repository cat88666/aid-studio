package com.sakura.common.error.rule;

import com.sakura.sakura.domain.SakuraProviderErrorRule;
import lombok.Data;

import java.util.List;

/**
 * 错误规则缓存文件 schema。
 * 序列化到 {@code ${user.home}/.sakura/cache/error-rules.json}，
 * 启动时优先反序列化此文件，DB 真正调用仅在缓存不存在/损坏时触发。
 *
 * @author 视觉AID
 */
@Data
public class ErrorRuleCacheSnapshot {

    /** schema 版本，破坏性变更时递增 */
    public static final String CURRENT_VERSION = "1.0";

    private String version;
    private String exportTime;
    private int ruleCount;
    private List<SakuraProviderErrorRule> rules;
}
