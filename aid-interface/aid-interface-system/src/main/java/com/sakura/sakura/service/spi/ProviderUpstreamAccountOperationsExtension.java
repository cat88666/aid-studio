package com.sakura.sakura.service.spi;

import com.sakura.sakura.domain.SakuraAiProvider;

import java.util.Map;

/** 可插拔的供应商账户查询扩展。 */
public interface ProviderUpstreamAccountOperationsExtension {
    boolean supports(String providerCode);

    Map<String, Object> capabilities(SakuraAiProvider provider);

    Map<String, Object> balance(SakuraAiProvider provider, Long startTime, Long endTime, String resourcePackName);
}
