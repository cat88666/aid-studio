package com.sakura.common.error;

import cn.hutool.core.util.StrUtil;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 根据模型编码解析错误规则使用的厂商编码。
 *
 * @author 视觉AID
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorProviderResolver
{
    private static final long CACHE_TTL_MILLIS = 5L * 60L * 1000L;
    private static final int CACHE_MAX_SIZE = 1024;
    private static final String EMPTY_PROVIDER_CODE = "";

    private final ISakuraAiModelService aiModelService;
    private final ISakuraAiProviderService aiProviderService;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public String resolve(String modelCode)
    {
        if (StrUtil.isBlank(modelCode))
        {
            return null;
        }
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(modelCode);
        if (Objects.nonNull(cached) && cached.expiresAt() > now)
        {
            return StrUtil.blankToDefault(cached.providerCode(), null);
        }

        synchronized (cache)
        {
            now = System.currentTimeMillis();
            cached = cache.get(modelCode);
            if (Objects.nonNull(cached) && cached.expiresAt() > now)
            {
                return StrUtil.blankToDefault(cached.providerCode(), null);
            }

            try
            {
                String providerCode = loadProviderCode(modelCode);
                removeExpiredEntries(now);
                if (cache.size() >= CACHE_MAX_SIZE)
                {
                    cache.remove(cache.keySet().iterator().next());
                }
                cache.put(modelCode, new CacheEntry(
                        StrUtil.blankToDefault(providerCode, EMPTY_PROVIDER_CODE),
                        System.currentTimeMillis() + CACHE_TTL_MILLIS));
                return providerCode;
            }
            catch (Exception e)
            {
                // 查询异常不写入负缓存，避免数据库短暂抖动导致五分钟内持续降级。
                log.error("错误分类解析厂商失败: modelCode={}", modelCode, e);
                return null;
            }
        }
    }

    private void removeExpiredEntries(long now)
    {
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private String loadProviderCode(String modelCode)
    {
        // 查询字段精简：厂商解析只需要厂商主键。
        SakuraAiModel model = aiModelService.getOne(
                Wrappers.<SakuraAiModel>lambdaQuery()
                        .select(SakuraAiModel::getProviderId)
                        .eq(SakuraAiModel::getModelCode, modelCode)
                        .eq(SakuraAiModel::getDelFlag, "0")
                        .last("LIMIT 1"),
                false);
        if (Objects.isNull(model) || Objects.isNull(model.getProviderId()))
        {
            return null;
        }
        // 查询字段精简：错误规则匹配只需要厂商编码。
        SakuraAiProvider provider = aiProviderService.getOne(
                Wrappers.<SakuraAiProvider>lambdaQuery()
                        .select(SakuraAiProvider::getProviderCode)
                        .eq(SakuraAiProvider::getId, model.getProviderId())
                        .last("LIMIT 1"),
                false);
        return Objects.isNull(provider) ? null : provider.getProviderCode();
    }

    private record CacheEntry(String providerCode, long expiresAt)
    {
    }
}
