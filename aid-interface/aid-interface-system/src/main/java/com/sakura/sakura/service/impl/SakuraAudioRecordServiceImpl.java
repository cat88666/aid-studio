package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraAudioRecordMapper;
import com.sakura.sakura.domain.SakuraAudioRecord;
import com.sakura.sakura.service.ISakuraAudioRecordService;

/**
 * 分镜配音业务记录 Service 实现
 *
 * @author 视觉AID
 */
@Service
public class SakuraAudioRecordServiceImpl extends ServiceImpl<SakuraAudioRecordMapper, SakuraAudioRecord> implements ISakuraAudioRecordService
{
    @Override
    public SakuraAudioRecord selectSakuraAudioRecordById(Long id)
    {
        return this.getById(id);
    }

    @Override
    public List<SakuraAudioRecord> selectSakuraAudioRecordList(SakuraAudioRecord sakuraAudioRecord)
    {
        LambdaQueryWrapper<SakuraAudioRecord> wrapper = Wrappers.lambdaQuery();
        if (sakuraAudioRecord != null)
        {
            if (sakuraAudioRecord.getUserId() != null)
            {
                wrapper.eq(SakuraAudioRecord::getUserId, sakuraAudioRecord.getUserId());
            }
            if (sakuraAudioRecord.getProjectId() != null)
            {
                wrapper.eq(SakuraAudioRecord::getProjectId, sakuraAudioRecord.getProjectId());
            }
            if (sakuraAudioRecord.getEpisodeId() != null)
            {
                wrapper.eq(SakuraAudioRecord::getEpisodeId, sakuraAudioRecord.getEpisodeId());
            }
            if (sakuraAudioRecord.getStoryboardId() != null)
            {
                wrapper.eq(SakuraAudioRecord::getStoryboardId, sakuraAudioRecord.getStoryboardId());
            }
            if (sakuraAudioRecord.getAudioSource() != null)
            {
                wrapper.eq(SakuraAudioRecord::getAudioSource, sakuraAudioRecord.getAudioSource());
            }
            if (sakuraAudioRecord.getVoiceModelId() != null)
            {
                wrapper.eq(SakuraAudioRecord::getVoiceModelId, sakuraAudioRecord.getVoiceModelId());
            }
            if (sakuraAudioRecord.getVoiceLibraryId() != null)
            {
                wrapper.eq(SakuraAudioRecord::getVoiceLibraryId, sakuraAudioRecord.getVoiceLibraryId());
            }
            if (StrUtil.isNotBlank(sakuraAudioRecord.getTimbreCode()))
            {
                wrapper.eq(SakuraAudioRecord::getTimbreCode, sakuraAudioRecord.getTimbreCode());
            }
            if (sakuraAudioRecord.getEnableLipSync() != null)
            {
                wrapper.eq(SakuraAudioRecord::getEnableLipSync, sakuraAudioRecord.getEnableLipSync());
            }
            if (StrUtil.isNotBlank(sakuraAudioRecord.getStatus()))
            {
                wrapper.eq(SakuraAudioRecord::getStatus, sakuraAudioRecord.getStatus());
            }
        }
        wrapper.orderByDesc(SakuraAudioRecord::getId);
        return this.list(wrapper);
    }

    @Override
    public int insertSakuraAudioRecord(SakuraAudioRecord sakuraAudioRecord)
    {
        sakuraAudioRecord.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraAudioRecord) ? 1 : 0;
    }

    @Override
    public int updateSakuraAudioRecord(SakuraAudioRecord sakuraAudioRecord)
    {
        sakuraAudioRecord.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraAudioRecord) ? 1 : 0;
    }

    @Override
    public int deleteSakuraAudioRecordByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    @Override
    public int deleteSakuraAudioRecordById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
