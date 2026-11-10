package com.sakura.rps.voice.service.impl;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.domain.SakuraAiVoiceLibrary;
import com.sakura.sakura.domain.SakuraReferenceAudio;
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.domain.SakuraRoleVoiceBinding;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.sakura.sakura.service.ISakuraAiVoiceLibraryService;
import com.sakura.sakura.service.ISakuraReferenceAudioService;
import com.sakura.sakura.service.ISakuraRolePropSceneService;
import com.sakura.sakura.service.ISakuraRoleVoiceBindingService;
import com.sakura.common.utils.DateUtils;
import com.sakura.rps.voice.dto.RoleVoiceBindRequest;
import com.sakura.rps.voice.service.IRoleVoiceBindingBusinessService;
import com.sakura.rps.voice.vo.RoleVoiceBindingVO;
import com.sakura.voice.util.VoiceEmotionCapability;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 角色音色绑定 业务 Service 实现。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class RoleVoiceBindingBusinessServiceImpl implements IRoleVoiceBindingBusinessService
{
    /** 删除标志：存在 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：删除 */
    private static final String DEL_FLAG_DELETED = "2";

    /** 状态：启用 */
    private static final String STATUS_NORMAL = "0";

    /** 状态：停用 */
    private static final String STATUS_DISABLED = "1";

    /** 角色资产类型 */
    private static final String ASSET_TYPE_CHARACTER = "character";

    /** 剧集占位值：参考音频标记为全剧集通用 */
    private static final long EPISODE_ID_NONE = 0L;

    /** 覆盖语速合法区间 */
    private static final BigDecimal SPEED_MIN = new BigDecimal("0.50");
    private static final BigDecimal SPEED_MAX = new BigDecimal("2.00");

    /** 覆盖音调合法区间 */
    private static final BigDecimal PITCH_MIN = new BigDecimal("-12.00");
    private static final BigDecimal PITCH_MAX = new BigDecimal("12.00");
    @Resource
    private ISakuraRoleVoiceBindingService bindingService;

    @Resource
    private ISakuraRolePropSceneService rpsService;

    @Resource
    private ISakuraAiVoiceLibraryService voiceLibraryService;

    @Resource
    private ISakuraAiModelService aiModelService;

    @Resource
    private ISakuraAiProviderService aiProviderService;

    @Resource
    private ISakuraReferenceAudioService referenceAudioService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVoiceBindingVO bindVoice(RoleVoiceBindRequest request, Long userId)
    {
        if (Objects.isNull(request) || Objects.isNull(userId))
        {
            log.info("角色音色绑定失败，请求或用户为空");
            throw new RuntimeException("绑定失败");
        }
        SakuraRolePropScene asset = loadOwnedCharacter(request.getAssetId(), userId);

        SakuraAiVoiceLibrary voice = loadActiveVoiceLibrary(request.getVoiceLibraryId());

        validateOverrideSpeed(request.getOverrideSpeed());
        validateOverridePitch(request.getOverridePitch());
        validateOverrideEmotion(request.getOverrideEmotion(), voice);

        SakuraRoleVoiceBinding existing = findActiveBinding(request.getAssetId());
        Date now = DateUtils.getNowDate();
        String operator = String.valueOf(userId);
        SakuraRoleVoiceBinding entity = Objects.nonNull(existing) ? existing : new SakuraRoleVoiceBinding();
        String previousSampleUrl = Objects.nonNull(existing) ? existing.getSampleUrl() : null;

        // 归属字段（每次都按角色最新数据回写，避免角色换项目后绑定表数据脱节）
        entity.setAssetId(asset.getId());
        entity.setProjectId(asset.getProjectId());
        entity.setEpisodeId(asset.getEpisodeId());
        entity.setUserId(userId);

        // 音色关联 + 展示冗余：整行覆盖
        fillBindingFromVoice(entity, voice);

        // 上传参考音频：叠加在音色之上，传 null 表示不绑定 / 解除已有绑定
        SakuraReferenceAudio referenceAudio = loadOwnedReferenceAudio(
                request.getReferenceAudioId(), userId, asset.getProjectId(), asset.getEpisodeId());
        fillBindingFromReferenceAudio(entity, referenceAudio);

        // 覆盖参数
        entity.setOverrideSpeed(request.getOverrideSpeed());
        entity.setOverridePitch(request.getOverridePitch());
        entity.setOverrideEmotion(StrUtil.isBlank(request.getOverrideEmotion())
                ? null : request.getOverrideEmotion().trim());

        entity.setStatus(STATUS_NORMAL);
        entity.setDelFlag(DEL_FLAG_NORMAL);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);

        boolean ok;
        try
        {
            if (Objects.isNull(existing))
            {
                entity.setCreateBy(operator);
                entity.setCreateTime(now);
                ok = bindingService.save(entity);
            }
            else
            {
                ok = updateBinding(entity, previousSampleUrl);
            }
        }
        catch (Exception e)
        {
            // 数据库异常（如唯一键冲突 / 约束违反 / 连接异常等）：
            // 不把底层英文 / 长异常直接抛出，统一归一化成短文案 "绑定失败"，
            // 保留 log 便于排查。
            log.error("角色音色绑定写库异常: assetId={}, voiceLibraryId={}, userId={}, err={}",
                    request.getAssetId(), request.getVoiceLibraryId(), userId, e.getMessage(), e);
            throw new RuntimeException("绑定失败");
        }
        if (!ok)
        {
            log.error("角色音色绑定写库失败: assetId={}, voiceLibraryId={}, userId={}",
                    request.getAssetId(), request.getVoiceLibraryId(), userId);
            throw new RuntimeException("绑定失败");
        }
        log.info("角色音色绑定成功: assetId={}, voiceLibraryId={}, bindingId={}, userId={}",
                entity.getAssetId(), entity.getVoiceLibraryId(), entity.getId(), userId);

        // 绑定入口已校验音色启用未删（loadActiveVoiceLibrary），此处按可用返回
        return toVO(entity, voice, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindVoice(Long assetId, Long userId)
    {
        if (Objects.isNull(assetId) || Objects.isNull(userId))
        {
            log.info("角色音色解绑失败，参数为空");
            throw new RuntimeException("解绑失败");
        }
        // 归属校验（asset 存在 + 是本人 character；不存在直接报错）
        loadOwnedCharacter(assetId, userId);

        SakuraRoleVoiceBinding existing = findActiveBinding(assetId);
        if (Objects.isNull(existing))
        {
            // 幂等：没有绑定也不报错
            log.info("角色未绑定音色，解绑无操作: assetId={}, userId={}", assetId, userId);
            return;
        }
        SakuraRoleVoiceBinding update = new SakuraRoleVoiceBinding();
        update.setId(existing.getId());
        update.setDelFlag(DEL_FLAG_DELETED);
        update.setUpdateBy(String.valueOf(userId));
        update.setUpdateTime(DateUtils.getNowDate());
        boolean ok;
        try
        {
            ok = bindingService.updateById(update);
        }
        catch (Exception e)
        {
            // 兜底：解绑写库异常（例如生成列 / 唯一键等约束触发）统一归一化短文案
            log.error("角色音色解绑写库异常: assetId={}, bindingId={}, userId={}, err={}",
                    assetId, existing.getId(), userId, e.getMessage(), e);
            throw new RuntimeException("解绑失败");
        }
        if (!ok)
        {
            log.error("角色音色解绑写库失败: assetId={}, bindingId={}, userId={}",
                    assetId, existing.getId(), userId);
            throw new RuntimeException("解绑失败");
        }
        log.info("角色音色解绑成功: assetId={}, bindingId={}, userId={}",
                assetId, existing.getId(), userId);
    }

    @Override
    public RoleVoiceBindingVO queryByAssetId(Long assetId, Long userId)
    {
        if (Objects.isNull(assetId) || Objects.isNull(userId))
        {
            log.info("角色音色查询失败，参数为空");
            throw new RuntimeException("角色不存在");
        }
        // 归属校验（asset 存在 + 是本人 character）
        loadOwnedCharacter(assetId, userId);

        SakuraRoleVoiceBinding binding = findActiveBinding(assetId);
        if (Objects.isNull(binding))
        {
            return null;
        }
        // 实时读音色库，拿到最新能力 + offline_time + 展示字段
        SakuraAiVoiceLibrary voice = voiceLibraryService.getById(binding.getVoiceLibraryId());
        // 可用性判定：音色启用未删 且 所属模型/供应商均启用
        boolean usable = Objects.nonNull(voice)
                && Boolean.TRUE.equals(resolveVoiceUsableMap(Collections.singletonList(voice)).get(voice.getId()));
        return toVO(binding, voice, usable);
    }

    @Override
    public Map<Long, RoleVoiceBindingVO> queryByAssetIds(Collection<Long> assetIds, Long userId)
    {
        if (CollectionUtil.isEmpty(assetIds) || Objects.isNull(userId))
        {
            return Collections.emptyMap();
        }
        // 批查绑定表：仅返回活跃 + 归属当前用户
        LambdaQueryWrapper<SakuraRoleVoiceBinding> wrapper = Wrappers.lambdaQuery();
        wrapper.select(SakuraRoleVoiceBinding::getId, SakuraRoleVoiceBinding::getAssetId,
                SakuraRoleVoiceBinding::getUserId, SakuraRoleVoiceBinding::getVoiceLibraryId,
                SakuraRoleVoiceBinding::getVoiceCode, SakuraRoleVoiceBinding::getVoiceName,
                SakuraRoleVoiceBinding::getAvatarUrl, SakuraRoleVoiceBinding::getSampleUrl,
                SakuraRoleVoiceBinding::getSampleText, SakuraRoleVoiceBinding::getLanguage,
                SakuraRoleVoiceBinding::getGender, SakuraRoleVoiceBinding::getAgeRange,
                SakuraRoleVoiceBinding::getModelId, SakuraRoleVoiceBinding::getProviderId,
                SakuraRoleVoiceBinding::getOverrideSpeed, SakuraRoleVoiceBinding::getOverridePitch,
                SakuraRoleVoiceBinding::getOverrideEmotion, SakuraRoleVoiceBinding::getReferenceAudioId,
                SakuraRoleVoiceBinding::getReferenceAudioUrl, SakuraRoleVoiceBinding::getReferenceAudioDurationMs,
                SakuraRoleVoiceBinding::getStatus, SakuraRoleVoiceBinding::getDelFlag);
        wrapper.in(SakuraRoleVoiceBinding::getAssetId, assetIds);
        wrapper.eq(SakuraRoleVoiceBinding::getUserId, userId);
        wrapper.eq(SakuraRoleVoiceBinding::getDelFlag, DEL_FLAG_NORMAL);
        wrapper.eq(SakuraRoleVoiceBinding::getStatus, STATUS_NORMAL);
        List<SakuraRoleVoiceBinding> bindings = bindingService.list(wrapper);
        if (CollectionUtil.isEmpty(bindings))
        {
            return Collections.emptyMap();
        }

        // 批查音色库：一次拿到所有涉及音色的能力字段 + offline_time + 状态 + 实时展示字段（头像/试听）
        Set<Long> voiceIds = bindings.stream()
                .map(SakuraRoleVoiceBinding::getVoiceLibraryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SakuraAiVoiceLibrary> voiceMap = new HashMap<>(voiceIds.size());
        if (CollectionUtil.isNotEmpty(voiceIds))
        {
            LambdaQueryWrapper<SakuraAiVoiceLibrary> vwrapper = Wrappers.lambdaQuery();
            vwrapper.select(SakuraAiVoiceLibrary::getId,
                    SakuraAiVoiceLibrary::getSupportsEmotion, SakuraAiVoiceLibrary::getSupportsSpeed,
                    SakuraAiVoiceLibrary::getSupportsPitch, SakuraAiVoiceLibrary::getDefaultSpeed,
                    SakuraAiVoiceLibrary::getDefaultPitch, SakuraAiVoiceLibrary::getOfflineTime,
                    SakuraAiVoiceLibrary::getStatus, SakuraAiVoiceLibrary::getDelFlag,
                    SakuraAiVoiceLibrary::getModelId, SakuraAiVoiceLibrary::getProviderId,
                    SakuraAiVoiceLibrary::getAvatarUrl, SakuraAiVoiceLibrary::getSampleUrl,
                    SakuraAiVoiceLibrary::getSampleText);
            vwrapper.in(SakuraAiVoiceLibrary::getId, voiceIds);
            List<SakuraAiVoiceLibrary> voices = voiceLibraryService.list(vwrapper);
            if (CollectionUtil.isNotEmpty(voices))
            {
                voiceMap = voices.stream()
                        .collect(Collectors.toMap(SakuraAiVoiceLibrary::getId, v -> v, (a, b) -> a));
            }
        }
        // 批量可用性判定（音色启用未删 且 模型/供应商启用）
        Map<Long, Boolean> usableMap = resolveVoiceUsableMap(voiceMap.values());

        Map<Long, RoleVoiceBindingVO> result = new HashMap<>(bindings.size());
        for (SakuraRoleVoiceBinding b : bindings)
        {
            SakuraAiVoiceLibrary voice = voiceMap.get(b.getVoiceLibraryId());
            boolean usable = Objects.nonNull(voice)
                    && Boolean.TRUE.equals(usableMap.get(voice.getId()));
            result.put(b.getAssetId(), toVO(b, voice, usable));
        }
        return result;
    }

    /**
     * 批量判定音色可用性：音色 {@code status=启用 && del_flag=0}，且所属模型、供应商均为启用未删。
     * 运营停用音色 / 模型 / 供应商后，C 端绑定信息立即按「已下架」口径返回（offline=true）。
     *
     * @param voices 待判定音色集合（需已带 status/delFlag/modelId/providerId 字段）
     * @return 音色ID → 是否可用
     */
    private Map<Long, Boolean> resolveVoiceUsableMap(Collection<SakuraAiVoiceLibrary> voices)
    {
        Map<Long, Boolean> result = new HashMap<>();
        if (CollectionUtil.isEmpty(voices))
        {
            return result;
        }
        // 收集涉及的模型/供应商ID
        Set<Long> modelIds = new HashSet<>();
        Set<Long> providerIds = new HashSet<>();
        for (SakuraAiVoiceLibrary v : voices)
        {
            if (Objects.nonNull(v.getModelId()))
            {
                modelIds.add(v.getModelId());
            }
            if (Objects.nonNull(v.getProviderId()))
            {
                providerIds.add(v.getProviderId());
            }
        }
        // 启用模型ID集合（查询字段精简：仅 id）
        Set<Long> enabledModelIds = new HashSet<>();
        if (CollectionUtil.isNotEmpty(modelIds))
        {
            LambdaQueryWrapper<SakuraAiModel> mw = Wrappers.lambdaQuery();
            mw.select(SakuraAiModel::getId);
            mw.in(SakuraAiModel::getId, modelIds);
            mw.eq(SakuraAiModel::getStatus, STATUS_NORMAL);
            mw.eq(SakuraAiModel::getDelFlag, DEL_FLAG_NORMAL);
            for (SakuraAiModel m : aiModelService.list(mw))
            {
                enabledModelIds.add(m.getId());
            }
        }
        // 启用供应商ID集合（查询字段精简：仅 id）
        Set<Long> enabledProviderIds = new HashSet<>();
        if (CollectionUtil.isNotEmpty(providerIds))
        {
            LambdaQueryWrapper<SakuraAiProvider> pw = Wrappers.lambdaQuery();
            pw.select(SakuraAiProvider::getId);
            pw.in(SakuraAiProvider::getId, providerIds);
            pw.eq(SakuraAiProvider::getStatus, STATUS_NORMAL);
            pw.eq(SakuraAiProvider::getDelFlag, DEL_FLAG_NORMAL);
            for (SakuraAiProvider p : aiProviderService.list(pw))
            {
                enabledProviderIds.add(p.getId());
            }
        }
        for (SakuraAiVoiceLibrary v : voices)
        {
            boolean usable = Objects.equals(STATUS_NORMAL, v.getStatus())
                    && Objects.equals(DEL_FLAG_NORMAL, v.getDelFlag())
                    && Objects.nonNull(v.getModelId()) && enabledModelIds.contains(v.getModelId())
                    && Objects.nonNull(v.getProviderId()) && enabledProviderIds.contains(v.getProviderId());
            result.put(v.getId(), usable);
        }
        return result;
    }
    /**
     * 校验角色归属：asset 存在、未删除、属于当前用户、且 asset_type=character。
     */
    private SakuraRolePropScene loadOwnedCharacter(Long assetId, Long userId)
    {
        SakuraRolePropScene asset = rpsService.getById(assetId);
        if (Objects.isNull(asset) || !Objects.equals(DEL_FLAG_NORMAL, asset.getDelFlag()))
        {
            log.info("角色音色绑定失败，角色不存在: assetId={}, userId={}", assetId, userId);
            throw new RuntimeException("角色不存在");
        }
        if (!Objects.equals(userId, asset.getUserId()))
        {
            log.info("角色音色绑定失败，角色不属于当前用户: assetId={}, ownerId={}, userId={}",
                    assetId, asset.getUserId(), userId);
            throw new RuntimeException("角色不存在");
        }
        if (!Objects.equals(ASSET_TYPE_CHARACTER, asset.getAssetType()))
        {
            log.info("角色音色绑定失败，资产不是角色: assetId={}, assetType={}, userId={}",
                    assetId, asset.getAssetType(), userId);
            throw new RuntimeException("不是角色");
        }
        return asset;
    }

    /**
     * 校验并加载所绑定的上传参考音频：须归属当前用户、同一项目，且为本剧集专属或全剧集通用（episode_id=0），
     * 同时启用未删。传 null 表示不绑定，直接返回 null。
     *
     * @param referenceAudioId 参考音频ID（可空）
     * @param userId           当前用户ID
     * @param projectId        角色所属项目ID
     * @param episodeId        角色所属剧集ID（可空，按 0 处理）
     * @return 参考音频实体；未传 ID 返回 null
     */
    private SakuraReferenceAudio loadOwnedReferenceAudio(Long referenceAudioId, Long userId,
            Long projectId, Long episodeId)
    {
        if (Objects.isNull(referenceAudioId))
        {
            return null;
        }
        long scopedEpisodeId = Objects.isNull(episodeId) ? EPISODE_ID_NONE : episodeId;
        SakuraReferenceAudio audio = referenceAudioService.getOne(Wrappers.<SakuraReferenceAudio>lambdaQuery()
                .select(SakuraReferenceAudio::getId, SakuraReferenceAudio::getAudioUrl,
                        SakuraReferenceAudio::getDurationMs)
                .eq(SakuraReferenceAudio::getId, referenceAudioId)
                .eq(SakuraReferenceAudio::getUserId, userId)
                .eq(SakuraReferenceAudio::getProjectId, projectId)
                .and(inner -> inner.eq(SakuraReferenceAudio::getEpisodeId, scopedEpisodeId)
                        .or().eq(SakuraReferenceAudio::getEpisodeId, EPISODE_ID_NONE))
                .eq(SakuraReferenceAudio::getStatus, STATUS_NORMAL)
                .eq(SakuraReferenceAudio::getDelFlag, DEL_FLAG_NORMAL)
                .last("LIMIT 1"), false);
        if (Objects.isNull(audio))
        {
            log.info("角色音色绑定失败，参考音频不存在: referenceAudioId={}, userId={}, projectId={}, episodeId={}",
                    referenceAudioId, userId, projectId, scopedEpisodeId);
            throw new RuntimeException("音频不存在");
        }
        return audio;
    }

    /**
     * 校验音色：存在、未删除、status=启用、offline_time 大于 NOW()。
     */
    private SakuraAiVoiceLibrary loadActiveVoiceLibrary(Long voiceLibraryId)
    {
        SakuraAiVoiceLibrary voice = voiceLibraryService.getById(voiceLibraryId);
        if (Objects.isNull(voice) || !Objects.equals(DEL_FLAG_NORMAL, voice.getDelFlag()))
        {
            log.info("角色音色绑定失败，音色不存在: voiceLibraryId={}", voiceLibraryId);
            throw new RuntimeException("音色不存在");
        }
        if (Objects.equals(STATUS_DISABLED, voice.getStatus()))
        {
            log.info("角色音色绑定失败，音色已停用: voiceLibraryId={}", voiceLibraryId);
            throw new RuntimeException("音色不存在");
        }
        Date offlineTime = voice.getOfflineTime();
        if (Objects.nonNull(offlineTime) && offlineTime.getTime() <= System.currentTimeMillis())
        {
            log.info("角色音色绑定失败，音色已下架: voiceLibraryId={}, offlineTime={}",
                    voiceLibraryId, offlineTime);
            throw new RuntimeException("音色已下架");
        }
        return voice;
    }

    /** 覆盖语速校验：null 跳过；不为 null 要在 [0.50, 2.00] 区间内。 */
    private void validateOverrideSpeed(BigDecimal speed)
    {
        if (Objects.isNull(speed))
        {
            return;
        }
        if (speed.compareTo(SPEED_MIN) < 0 || speed.compareTo(SPEED_MAX) > 0)
        {
            log.info("角色音色绑定失败，覆盖语速越界: speed={}", speed);
            throw new RuntimeException("语速越界");
        }
    }

    /** 覆盖音调校验：null 跳过；不为 null 要在 [-12.00, 12.00] 区间内。 */
    private void validateOverridePitch(BigDecimal pitch)
    {
        if (Objects.isNull(pitch))
        {
            return;
        }
        if (pitch.compareTo(PITCH_MIN) < 0 || pitch.compareTo(PITCH_MAX) > 0)
        {
            log.info("角色音色绑定失败，覆盖音调越界: pitch={}", pitch);
            throw new RuntimeException("音调越界");
        }
    }

    /**
     * 覆盖情感校验（以供应商声明为唯一标准，null/空跳过，无任何全局配置依赖）：
     * 音色须开启情感（supports_emotion=true）；情感编码须命中音色所属模型
     * {@code capability_json.emotions} 白名单（供应商官方声明的原生编码）。
     * 白名单为空 = 供应商未声明能力，不拦截——与配音链路 generateAudio 同口径。
     *
     * @param emotion 覆盖情感（供应商原生编码，可空）
     * @param voice   绑定的音色库记录（用于反查所属模型能力）
     */
    private void validateOverrideEmotion(String emotion, SakuraAiVoiceLibrary voice)
    {
        if (StrUtil.isBlank(emotion) || Objects.isNull(voice))
        {
            return;
        }
        String trimmed = emotion.trim();
        // 音色本身未开启情感参数：设覆盖情感无意义，直接拒绝引导用户换音色
        if (!Boolean.TRUE.equals(voice.getSupportsEmotion()))
        {
            log.info("角色音色绑定失败，音色不支持情感: voiceLibraryId={}, emotion={}", voice.getId(), trimmed);
            throw new RuntimeException("音色不支持情感");
        }
        if (Objects.isNull(voice.getModelId()))
        {
            return;
        }
        // 查询字段精简：仅 id/capability_json（能力白名单解析用），后续扩展取数请同步增列
        SakuraAiModel model = aiModelService.getOne(Wrappers.<SakuraAiModel>lambdaQuery()
                .select(SakuraAiModel::getId, SakuraAiModel::getCapabilityJson)
                .eq(SakuraAiModel::getId, voice.getModelId())
                .last("LIMIT 1"), false);
        if (Objects.isNull(model))
        {
            return;
        }
        List<String> supported = VoiceEmotionCapability.parseModelEmotions(model.getCapabilityJson());
        // 白名单为空 = 供应商未声明能力，不拦截（与配音链路同口径）
        if (CollectionUtil.isEmpty(supported))
        {
            return;
        }
        if (!supported.contains(trimmed))
        {
            log.info("角色音色绑定失败，情感不在模型白名单: voiceLibraryId={}, modelId={}, emotion={}, supported={}",
                    voice.getId(), voice.getModelId(), trimmed, supported);
            throw new RuntimeException("情感不支持");
        }
    }
    /** 查 asset_id 下的活跃绑定行；没有返回 null。 */
    private SakuraRoleVoiceBinding findActiveBinding(Long assetId)
    {
        LambdaQueryWrapper<SakuraRoleVoiceBinding> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraRoleVoiceBinding::getAssetId, assetId);
        wrapper.eq(SakuraRoleVoiceBinding::getDelFlag, DEL_FLAG_NORMAL);
        wrapper.last("limit 1");
        return bindingService.getOne(wrapper, false);
    }

    /**
     * 写回绑定行。两处冗余列在本次覆盖后可能需要清空，而 updateById 会跳过 null 字段，
     * 故凡涉及置空的场景一律走 LambdaUpdateWrapper 显式 set null：
     * 试听时长是 sample_url 的冗余，换音色后旧值失效，由出片解析按新样音重新探测回写；
     * 参考音频三列是上传音频的冗余快照，解除绑定后不清掉的话已解绑的音频仍会被出片链路下发给厂商。
     *
     * @param entity            待写回的绑定实体
     * @param previousSampleUrl 覆盖前的试听音频 URL
     * @return 是否写库成功
     */
    private boolean updateBinding(SakuraRoleVoiceBinding entity, String previousSampleUrl)
    {
        boolean sampleChanged = !Objects.equals(previousSampleUrl, entity.getSampleUrl());
        boolean referenceCleared = Objects.isNull(entity.getReferenceAudioId());
        if (!sampleChanged && !referenceCleared)
        {
            return bindingService.updateById(entity);
        }
        LambdaUpdateWrapper<SakuraRoleVoiceBinding> wrapper = Wrappers.<SakuraRoleVoiceBinding>lambdaUpdate()
                .eq(SakuraRoleVoiceBinding::getId, entity.getId());
        if (sampleChanged)
        {
            wrapper.set(SakuraRoleVoiceBinding::getSampleDurationMs, null);
        }
        if (referenceCleared)
        {
            wrapper.set(SakuraRoleVoiceBinding::getReferenceAudioId, null);
            wrapper.set(SakuraRoleVoiceBinding::getReferenceAudioUrl, null);
            wrapper.set(SakuraRoleVoiceBinding::getReferenceAudioDurationMs, null);
        }
        return bindingService.update(entity, wrapper);
    }

    /** 按音色库最新数据填充绑定表的冗余字段 + 音色关联。 */
    private void fillBindingFromVoice(SakuraRoleVoiceBinding entity, SakuraAiVoiceLibrary voice)
    {
        entity.setVoiceLibraryId(voice.getId());
        entity.setVoiceCode(voice.getVoiceCode());
        entity.setVoiceName(voice.getVoiceName());
        entity.setAvatarUrl(voice.getAvatarUrl());
        entity.setSampleUrl(voice.getSampleUrl());
        entity.setSampleText(voice.getSampleText());
        entity.setLanguage(voice.getLanguage());
        entity.setGender(voice.getGender());
        entity.setAgeRange(voice.getAgeRange());
        entity.setModelId(voice.getModelId());
        entity.setProviderId(voice.getProviderId());
    }

    /**
     * 按上传参考音频填充绑定表的冗余快照。
     * 快照下沉到绑定表是为了让出片解析零回查，与本表 sample_url / sample_duration_ms 的冗余风格一致；
     * 传 null（未绑定 / 解除绑定）时三列一并清空，实际置空由 {@link #updateBinding} 显式 set 完成。
     *
     * @param entity 待写回的绑定实体
     * @param audio  参考音频实体，可为 null
     */
    private void fillBindingFromReferenceAudio(SakuraRoleVoiceBinding entity, SakuraReferenceAudio audio)
    {
        if (Objects.isNull(audio))
        {
            entity.setReferenceAudioId(null);
            entity.setReferenceAudioUrl(null);
            entity.setReferenceAudioDurationMs(null);
            return;
        }
        entity.setReferenceAudioId(audio.getId());
        entity.setReferenceAudioUrl(audio.getAudioUrl());
        entity.setReferenceAudioDurationMs(audio.getDurationMs());
    }

    /**
     * 绑定实体 → VO。
     * 展示字段（头像 / 试听音频 / 试听文案）优先取实时音色库值（运营补图/换音频后 C 端立即生效），
     * 音色库为空值时回退绑定表冗余快照；能力字段、默认参数、offline_time 从实时的 voice_library 取。
     * {@code offline=true} 的口径：音色被硬删 / 已停用 / 已软删 / 所属模型或供应商已停用 / 已过下架时间。
     *
     * @param b           绑定实体
     * @param voice       实时音色库记录（可为 null）
     * @param voiceUsable 音色可用性（音色启用未删 且 模型/供应商启用）
     */
    private RoleVoiceBindingVO toVO(SakuraRoleVoiceBinding b, SakuraAiVoiceLibrary voice, boolean voiceUsable)
    {
        RoleVoiceBindingVO vo = new RoleVoiceBindingVO();
        vo.setBindingId(b.getId());
        vo.setAssetId(b.getAssetId());
        vo.setVoiceLibraryId(b.getVoiceLibraryId());
        vo.setVoiceCode(b.getVoiceCode());
        vo.setVoiceName(b.getVoiceName());
        // 头像/试听实时优先：音色库有值用音色库，否则回退绑定快照
        vo.setAvatarUrl(preferLive(Objects.nonNull(voice) ? voice.getAvatarUrl() : null, b.getAvatarUrl()));
        vo.setSampleUrl(preferLive(Objects.nonNull(voice) ? voice.getSampleUrl() : null, b.getSampleUrl()));
        vo.setSampleText(preferLive(Objects.nonNull(voice) ? voice.getSampleText() : null, b.getSampleText()));
        vo.setLanguage(b.getLanguage());
        vo.setGender(b.getGender());
        vo.setAgeRange(b.getAgeRange());
        vo.setOverrideSpeed(b.getOverrideSpeed());
        vo.setOverridePitch(b.getOverridePitch());
        vo.setOverrideEmotion(b.getOverrideEmotion());
        // 参考音频是用户上传的独立资源，与音色库无关，直接回显绑定表快照
        vo.setReferenceAudioId(b.getReferenceAudioId());
        vo.setReferenceAudioUrl(b.getReferenceAudioUrl());
        vo.setReferenceAudioDurationMs(b.getReferenceAudioDurationMs());

        if (Objects.nonNull(voice))
        {
            vo.setSupportsEmotion(voice.getSupportsEmotion());
            vo.setSupportsSpeed(voice.getSupportsSpeed());
            vo.setSupportsPitch(voice.getSupportsPitch());
            vo.setDefaultSpeed(voice.getDefaultSpeed());
            vo.setDefaultPitch(voice.getDefaultPitch());
            vo.setOfflineTime(voice.getOfflineTime());
            Date offlineTime = voice.getOfflineTime();
            boolean timeOffline = Objects.isNull(offlineTime)
                    || offlineTime.getTime() <= System.currentTimeMillis();
            // 停用（音色/模型/供应商）与到期下架同口径标记不可用
            vo.setOffline(timeOffline || !voiceUsable);
        }
        else
        {
            // 音色被硬删 / 查不到 → 视为已下架
            vo.setOffline(Boolean.TRUE);
        }
        return vo;
    }

    /** 实时值非空优先，否则回退快照值。 */
    private String preferLive(String liveValue, String snapshotValue)
    {
        return StrUtil.isNotBlank(liveValue) ? liveValue : snapshotValue;
    }
}
