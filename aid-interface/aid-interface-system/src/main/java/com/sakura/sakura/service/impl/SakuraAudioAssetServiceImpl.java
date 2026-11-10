package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraAudioAsset;
import com.sakura.sakura.mapper.SakuraAudioAssetMapper;
import com.sakura.sakura.service.ISakuraAudioAssetService;
import com.sakura.common.utils.DateUtils;
import org.springframework.stereotype.Service;

/**
 * 音频资产 Service 实现
 *
 * @author 视觉AID
 */
@Service
public class SakuraAudioAssetServiceImpl extends ServiceImpl<SakuraAudioAssetMapper, SakuraAudioAsset>
        implements ISakuraAudioAssetService
{
    /** 未删除 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 已删除（软删） */
    private static final String DEL_FLAG_DELETED = "2";

    @Override
    public SakuraAudioAsset selectSakuraAudioAssetById(Long id)
    {
        if (Objects.isNull(id) || id <= 0)
        {
            return null;
        }
        return this.getById(id);
    }

    @Override
    public List<SakuraAudioAsset> selectSakuraAudioAssetList(SakuraAudioAsset sakuraAudioAsset)
    {
        // 后台管理使用；显式 .select 防止未来新增字段时漏查
        LambdaQueryWrapper<SakuraAudioAsset> wrapper = Wrappers.lambdaQuery();
        wrapper.select(SakuraAudioAsset::getId,
                SakuraAudioAsset::getUserId, SakuraAudioAsset::getProjectId,
                SakuraAudioAsset::getEpisodeId, SakuraAudioAsset::getStoryboardId,
                SakuraAudioAsset::getAudioRecordId, SakuraAudioAsset::getMediaTaskId,
                SakuraAudioAsset::getAudioUrl,
                SakuraAudioAsset::getFileSize, SakuraAudioAsset::getAudioFormat,
                SakuraAudioAsset::getSampleRate,
                SakuraAudioAsset::getTtsText, SakuraAudioAsset::getVoiceLibraryId,
                SakuraAudioAsset::getVoiceModelId, SakuraAudioAsset::getVoiceCode,
                SakuraAudioAsset::getVoiceName, SakuraAudioAsset::getEmotion,
                SakuraAudioAsset::getSpeechRate, SakuraAudioAsset::getLoudnessRate,
                SakuraAudioAsset::getPitch,
                SakuraAudioAsset::getAudioSource, SakuraAudioAsset::getAssetTitle,
                SakuraAudioAsset::getRemark, SakuraAudioAsset::getDelFlag,
                SakuraAudioAsset::getCreateBy, SakuraAudioAsset::getCreateTime,
                SakuraAudioAsset::getUpdateBy, SakuraAudioAsset::getUpdateTime);
        if (Objects.nonNull(sakuraAudioAsset))
        {
            if (Objects.nonNull(sakuraAudioAsset.getUserId()))
            {
                wrapper.eq(SakuraAudioAsset::getUserId, sakuraAudioAsset.getUserId());
            }
            if (Objects.nonNull(sakuraAudioAsset.getProjectId()))
            {
                wrapper.eq(SakuraAudioAsset::getProjectId, sakuraAudioAsset.getProjectId());
            }
            if (Objects.nonNull(sakuraAudioAsset.getEpisodeId()))
            {
                wrapper.eq(SakuraAudioAsset::getEpisodeId, sakuraAudioAsset.getEpisodeId());
            }
            if (Objects.nonNull(sakuraAudioAsset.getStoryboardId()))
            {
                wrapper.eq(SakuraAudioAsset::getStoryboardId, sakuraAudioAsset.getStoryboardId());
            }
            if (Objects.nonNull(sakuraAudioAsset.getVoiceLibraryId()))
            {
                wrapper.eq(SakuraAudioAsset::getVoiceLibraryId, sakuraAudioAsset.getVoiceLibraryId());
            }
            if (StrUtil.isNotBlank(sakuraAudioAsset.getVoiceCode()))
            {
                wrapper.eq(SakuraAudioAsset::getVoiceCode, sakuraAudioAsset.getVoiceCode());
            }
            if (Objects.nonNull(sakuraAudioAsset.getAudioSource()))
            {
                wrapper.eq(SakuraAudioAsset::getAudioSource, sakuraAudioAsset.getAudioSource());
            }
            if (StrUtil.isNotBlank(sakuraAudioAsset.getDelFlag()))
            {
                wrapper.eq(SakuraAudioAsset::getDelFlag, sakuraAudioAsset.getDelFlag());
            }
            if (StrUtil.isNotBlank(sakuraAudioAsset.getAssetTitle()))
            {
                wrapper.like(SakuraAudioAsset::getAssetTitle, sakuraAudioAsset.getAssetTitle());
            }
        }
        wrapper.orderByDesc(SakuraAudioAsset::getId);
        return this.list(wrapper);
    }

    @Override
    public int insertSakuraAudioAsset(SakuraAudioAsset sakuraAudioAsset)
    {
        if (Objects.isNull(sakuraAudioAsset))
        {
            return 0;
        }
        // 创建审计兜底
        if (Objects.isNull(sakuraAudioAsset.getCreateTime()))
        {
            sakuraAudioAsset.setCreateTime(DateUtils.getNowDate());
        }
        if (StrUtil.isBlank(sakuraAudioAsset.getDelFlag()))
        {
            sakuraAudioAsset.setDelFlag(DEL_FLAG_NORMAL);
        }
        return this.save(sakuraAudioAsset) ? 1 : 0;
    }

    @Override
    public int updateSakuraAudioAsset(SakuraAudioAsset sakuraAudioAsset)
    {
        if (Objects.isNull(sakuraAudioAsset) || Objects.isNull(sakuraAudioAsset.getId()))
        {
            return 0;
        }
        sakuraAudioAsset.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraAudioAsset) ? 1 : 0;
    }

    @Override
    public int deleteSakuraAudioAssetByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        boolean ok = this.update(Wrappers.<SakuraAudioAsset>lambdaUpdate()
                .in(SakuraAudioAsset::getId, Arrays.asList(ids))
                .eq(SakuraAudioAsset::getDelFlag, DEL_FLAG_NORMAL)
                .set(SakuraAudioAsset::getDelFlag, DEL_FLAG_DELETED)
                .set(SakuraAudioAsset::getUpdateTime, DateUtils.getNowDate()));
        return ok ? 1 : 0;
    }

    @Override
    public int deleteSakuraAudioAssetById(Long id)
    {
        if (Objects.isNull(id) || id <= 0)
        {
            return 0;
        }
        SakuraAudioAsset update = new SakuraAudioAsset();
        update.setId(id);
        update.setDelFlag(DEL_FLAG_DELETED);
        update.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(update) ? 1 : 0;
    }

    @Override
    public SakuraAudioAsset selectByAudioRecordId(Long audioRecordId)
    {
        if (Objects.isNull(audioRecordId) || audioRecordId <= 0)
        {
            return null;
        }
        LambdaQueryWrapper<SakuraAudioAsset> wrapper = Wrappers.lambdaQuery();
        wrapper.select(SakuraAudioAsset::getId, SakuraAudioAsset::getAudioRecordId,
                SakuraAudioAsset::getAudioUrl, SakuraAudioAsset::getDelFlag);
        wrapper.eq(SakuraAudioAsset::getAudioRecordId, audioRecordId);
        wrapper.last("LIMIT 1");
        return this.getOne(wrapper, false);
    }
}
