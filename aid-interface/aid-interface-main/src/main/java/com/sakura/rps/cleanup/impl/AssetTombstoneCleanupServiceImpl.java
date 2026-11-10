package com.sakura.rps.cleanup.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.domain.SakuraRolePropSceneForm;
import com.sakura.sakura.domain.SakuraRolePropSceneFormImage;
import com.sakura.sakura.domain.SakuraRoleVoiceBinding;
import com.sakura.sakura.domain.SakuraScenePlot;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.service.ISakuraRolePropSceneFormImageService;
import com.sakura.sakura.service.ISakuraRolePropSceneFormService;
import com.sakura.sakura.service.ISakuraRolePropSceneService;
import com.sakura.sakura.service.ISakuraRoleVoiceBindingService;
import com.sakura.sakura.service.ISakuraScenePlotService;
import com.sakura.sakura.service.ISakuraStoryboardService;
import com.sakura.common.core.redis.RedisCache;
import com.sakura.media.cleanup.IMediaOssCleanupService;
import com.sakura.rps.cleanup.AssetCleanupProperties;
import com.sakura.rps.cleanup.IAssetTombstoneCleanupService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 自动覆盖资产墓碑清理服务实现。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class AssetTombstoneCleanupServiceImpl implements IAssetTombstoneCleanupService
{
    private static final String DEL_FLAG_NORMAL = "0";
    private static final String DEL_FLAG_DELETED = "2";
    private static final String DELETE_REASON_AUTO_OVERWRITE = "auto_overwrite";
    private static final String CREATE_SOURCE_AUTO = "auto";
    private static final String ASSET_TYPE_SCENE = "scene";
    private static final String LOCK_KEY = "sakura:asset-cleanup:lock";
    private static final long LOCK_TTL_HOURS = 6L;

    @Autowired
    private AssetCleanupProperties properties;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISakuraRolePropSceneService assetService;

    @Autowired
    private ISakuraRolePropSceneFormService formService;

    @Autowired
    private ISakuraRolePropSceneFormImageService formImageService;

    @Autowired
    private ISakuraRoleVoiceBindingService voiceBindingService;

    @Autowired
    private ISakuraScenePlotService scenePlotService;

    @Autowired
    private ISakuraStoryboardService storyboardService;

    @Autowired
    private IMediaOssCleanupService mediaOssCleanupService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public int cleanExpired()
    {
        if (!properties.isEnabled())
        {
            log.debug("自动覆盖资产墓碑清理已关闭");
            return 0;
        }
        String lockToken = IdUtil.fastSimpleUUID();
        if (!redisCache.setCacheObjectIfAbsent(LOCK_KEY, lockToken, LOCK_TTL_HOURS, TimeUnit.HOURS))
        {
            log.debug("其它实例正在清理自动覆盖资产墓碑，本轮跳过");
            return 0;
        }
        try
        {
            return doCleanExpired();
        }
        finally
        {
            if (!redisCache.deleteObjectIfValueEquals(LOCK_KEY, lockToken))
            {
                log.warn("自动覆盖资产清理锁已过期或被重新获取，跳过释放: token={}", lockToken);
            }
        }
    }

    private int doCleanExpired()
    {
        Date cutoff = Date.from(Instant.now().minus(properties.getRetentionDays(), ChronoUnit.DAYS));
        List<SakuraRolePropScene> candidates = assetService.list(
                Wrappers.<SakuraRolePropScene>lambdaQuery()
                        .select(SakuraRolePropScene::getId, SakuraRolePropScene::getAssetType,
                                SakuraRolePropScene::getDeletedAt, SakuraRolePropScene::getUpdateTime)
                        .eq(SakuraRolePropScene::getDelFlag, DEL_FLAG_DELETED)
                        .eq(SakuraRolePropScene::getDeleteReason, DELETE_REASON_AUTO_OVERWRITE)
                        .isNotNull(SakuraRolePropScene::getDeleteTaskId)
                        .lt(SakuraRolePropScene::getDeletedAt, cutoff)
                        .lt(SakuraRolePropScene::getUpdateTime, cutoff)
                        .orderByAsc(SakuraRolePropScene::getId)
                        .last("LIMIT " + properties.getBatchSize()));
        if (CollectionUtil.isEmpty(candidates))
        {
            return 0;
        }

        List<CleanupPlan> plans = new ArrayList<>();
        for (SakuraRolePropScene candidate : candidates)
        {
            try
            {
                CleanupPlan plan = loadCleanupPlan(candidate.getId(), cutoff);
                if (Objects.nonNull(plan))
                {
                    plans.add(plan);
                }
            }
            catch (Exception e)
            {
                log.error("自动覆盖资产墓碑清理计划加载失败，保留数据待下次重试: assetId={}",
                        candidate.getId(), e);
            }
        }

        Set<String> allFileUrls = new LinkedHashSet<>();
        plans.forEach(plan -> allFileUrls.addAll(plan.fileUrls()));
        Map<String, Boolean> fileResults = mediaOssCleanupService.cleanupFilesNowByFile(allFileUrls);

        int cleaned = 0;
        for (CleanupPlan plan : plans)
        {
            try
            {
                boolean fileFailed = plan.fileUrls().stream()
                        .anyMatch(url -> !Boolean.TRUE.equals(fileResults.get(url)));
                if (fileFailed)
                {
                    continue;
                }
                Boolean removed = transactionTemplate.execute(status -> removeTombstone(plan.assetId(), cutoff));
                if (Boolean.TRUE.equals(removed))
                {
                    cleaned++;
                }
            }
            catch (Exception e)
            {
                log.error("自动覆盖资产墓碑清理失败，保留数据待下次重试: assetId={}", plan.assetId(), e);
            }
        }
        log.info("自动覆盖资产墓碑清理完成: candidates={}, cleaned={}", candidates.size(), cleaned);
        return cleaned;
    }

    private CleanupPlan loadCleanupPlan(Long assetId, Date cutoff)
    {
        SakuraRolePropScene root = findEligibleRoot(assetId, cutoff, false);
        if (Objects.isNull(root) || hasProtectedAssociation(root))
        {
            return null;
        }
        List<SakuraRolePropSceneFormImage> images = formImageService.list(
                Wrappers.<SakuraRolePropSceneFormImage>lambdaQuery()
                        .select(SakuraRolePropSceneFormImage::getId, SakuraRolePropSceneFormImage::getImageUrl)
                        .eq(SakuraRolePropSceneFormImage::getAssetId, assetId)
                        .eq(SakuraRolePropSceneFormImage::getDelFlag, DEL_FLAG_DELETED));
        Set<String> urls = new LinkedHashSet<>();
        for (SakuraRolePropSceneFormImage image : images)
        {
            if (StrUtil.isNotBlank(image.getImageUrl()))
            {
                urls.add(image.getImageUrl().trim());
            }
        }
        return new CleanupPlan(assetId, urls);
    }

    private boolean removeTombstone(Long assetId, Date cutoff)
    {
        SakuraRolePropScene root = findEligibleRoot(assetId, cutoff, true);
        if (Objects.isNull(root))
        {
            return false;
        }
        if (hasProtectedAssociation(root))
        {
            log.warn("资产墓碑在物理删除前出现有效或人工关联，保留待核查: assetId={}", assetId);
            return false;
        }

        formImageService.remove(Wrappers.<SakuraRolePropSceneFormImage>lambdaQuery()
                .eq(SakuraRolePropSceneFormImage::getAssetId, assetId)
                .eq(SakuraRolePropSceneFormImage::getDelFlag, DEL_FLAG_DELETED));
        formService.remove(Wrappers.<SakuraRolePropSceneForm>lambdaQuery()
                .eq(SakuraRolePropSceneForm::getAssetId, assetId)
                .eq(SakuraRolePropSceneForm::getDelFlag, DEL_FLAG_DELETED));
        voiceBindingService.remove(Wrappers.<SakuraRoleVoiceBinding>lambdaQuery()
                .eq(SakuraRoleVoiceBinding::getAssetId, assetId)
                .eq(SakuraRoleVoiceBinding::getDelFlag, DEL_FLAG_DELETED));
        if (Objects.equals(ASSET_TYPE_SCENE, root.getAssetType()))
        {
            scenePlotService.remove(Wrappers.<SakuraScenePlot>lambdaQuery()
                    .eq(SakuraScenePlot::getSceneId, assetId)
                    .eq(SakuraScenePlot::getDelFlag, DEL_FLAG_DELETED)
                    .eq(SakuraScenePlot::getCreateSource, CREATE_SOURCE_AUTO));
        }

        boolean removed = assetService.remove(Wrappers.<SakuraRolePropScene>lambdaQuery()
                .eq(SakuraRolePropScene::getId, assetId)
                .eq(SakuraRolePropScene::getDelFlag, DEL_FLAG_DELETED)
                .eq(SakuraRolePropScene::getDeleteReason, DELETE_REASON_AUTO_OVERWRITE)
                .isNotNull(SakuraRolePropScene::getDeleteTaskId)
                .lt(SakuraRolePropScene::getDeletedAt, cutoff)
                .lt(SakuraRolePropScene::getUpdateTime, cutoff));
        if (!removed)
        {
            throw new IllegalStateException("资产墓碑状态已变化");
        }
        return true;
    }

    private SakuraRolePropScene findEligibleRoot(Long assetId, Date cutoff, boolean forUpdate)
    {
        var query = Wrappers.<SakuraRolePropScene>lambdaQuery()
                .select(SakuraRolePropScene::getId, SakuraRolePropScene::getAssetType)
                .eq(SakuraRolePropScene::getId, assetId)
                .eq(SakuraRolePropScene::getDelFlag, DEL_FLAG_DELETED)
                .eq(SakuraRolePropScene::getDeleteReason, DELETE_REASON_AUTO_OVERWRITE)
                .isNotNull(SakuraRolePropScene::getDeleteTaskId)
                .lt(SakuraRolePropScene::getDeletedAt, cutoff)
                .lt(SakuraRolePropScene::getUpdateTime, cutoff);
        if (forUpdate)
        {
            query.last("FOR UPDATE");
        }
        return assetService.getOne(query, false);
    }

    private boolean hasProtectedAssociation(SakuraRolePropScene root)
    {
        Long assetId = root.getId();
        if (formService.count(Wrappers.<SakuraRolePropSceneForm>lambdaQuery()
                .eq(SakuraRolePropSceneForm::getAssetId, assetId)
                .and(wrapper -> wrapper.isNull(SakuraRolePropSceneForm::getDelFlag)
                        .or().ne(SakuraRolePropSceneForm::getDelFlag, DEL_FLAG_DELETED))) > 0)
        {
            return true;
        }
        if (formImageService.count(Wrappers.<SakuraRolePropSceneFormImage>lambdaQuery()
                .eq(SakuraRolePropSceneFormImage::getAssetId, assetId)
                .and(wrapper -> wrapper.isNull(SakuraRolePropSceneFormImage::getDelFlag)
                        .or().ne(SakuraRolePropSceneFormImage::getDelFlag, DEL_FLAG_DELETED))) > 0)
        {
            return true;
        }
        if (voiceBindingService.count(Wrappers.<SakuraRoleVoiceBinding>lambdaQuery()
                .eq(SakuraRoleVoiceBinding::getAssetId, assetId)
                .and(wrapper -> wrapper.isNull(SakuraRoleVoiceBinding::getDelFlag)
                        .or().ne(SakuraRoleVoiceBinding::getDelFlag, DEL_FLAG_DELETED))) > 0)
        {
            return true;
        }
        if (!Objects.equals(ASSET_TYPE_SCENE, root.getAssetType()))
        {
            return false;
        }
        // 有效分镜仍按 source_scene_id 追踪该历史根时保留根数据；分镜名称快照负责当前场景解析，
        // 但不应在分镜仍有效时提前破坏其辅助追踪链。
        if (storyboardService.count(Wrappers.<SakuraStoryboard>lambdaQuery()
                .eq(SakuraStoryboard::getSourceSceneId, assetId)
                .eq(SakuraStoryboard::getDelFlag, DEL_FLAG_NORMAL)) > 0)
        {
            return true;
        }
        return scenePlotService.count(Wrappers.<SakuraScenePlot>lambdaQuery()
                .eq(SakuraScenePlot::getSceneId, assetId)
                .and(wrapper -> wrapper.isNull(SakuraScenePlot::getDelFlag)
                        .or().ne(SakuraScenePlot::getDelFlag, DEL_FLAG_DELETED)
                        .or().isNull(SakuraScenePlot::getCreateSource)
                        .or().ne(SakuraScenePlot::getCreateSource, CREATE_SOURCE_AUTO))) > 0;
    }

    private record CleanupPlan(Long assetId, Set<String> fileUrls)
    {
    }
}
