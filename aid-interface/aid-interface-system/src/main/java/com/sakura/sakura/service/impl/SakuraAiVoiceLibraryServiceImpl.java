package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraAiVoiceLibrary;
import com.sakura.sakura.mapper.SakuraAiVoiceLibraryMapper;
import com.sakura.sakura.service.ISakuraAiVoiceLibraryService;
import com.sakura.common.utils.DateUtils;
import org.springframework.stereotype.Service;

/**
 * AI音色库 Service 实现（基础 CRUD）
 * 本类仅承载裸 CRUD 与简单条件组装；字段校验、{@code model_id} 反查、
 * 标签字典命中校验等业务编排放在 {@code aid-interface-main} 的业务 Service。
 *
 * @author 视觉AID
 */
@Service
public class SakuraAiVoiceLibraryServiceImpl extends ServiceImpl<SakuraAiVoiceLibraryMapper, SakuraAiVoiceLibrary> implements ISakuraAiVoiceLibraryService
{
    /** 删除标志：存在 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：已删除 */
    private static final String DEL_FLAG_DELETED = "2";

    @Override
    public SakuraAiVoiceLibrary selectSakuraAiVoiceLibraryById(Long id)
    {
        return this.getById(id);
    }

    @Override
    public List<SakuraAiVoiceLibrary> selectSakuraAiVoiceLibraryList(SakuraAiVoiceLibrary query)
    {
        LambdaQueryWrapper<SakuraAiVoiceLibrary> wrapper = Wrappers.lambdaQuery();
        if (query != null)
        {
            if (query.getProviderId() != null)
            {
                wrapper.eq(SakuraAiVoiceLibrary::getProviderId, query.getProviderId());
            }
            if (query.getModelId() != null)
            {
                wrapper.eq(SakuraAiVoiceLibrary::getModelId, query.getModelId());
            }
            if (StrUtil.isNotBlank(query.getLanguage()))
            {
                wrapper.eq(SakuraAiVoiceLibrary::getLanguage, query.getLanguage());
            }
            if (StrUtil.isNotBlank(query.getGender()))
            {
                wrapper.eq(SakuraAiVoiceLibrary::getGender, query.getGender());
            }
            if (StrUtil.isNotBlank(query.getAgeRange()))
            {
                wrapper.eq(SakuraAiVoiceLibrary::getAgeRange, query.getAgeRange());
            }
            if (StrUtil.isNotBlank(query.getStatus()))
            {
                wrapper.eq(SakuraAiVoiceLibrary::getStatus, query.getStatus());
            }
            if (StrUtil.isNotBlank(query.getDelFlag()))
            {
                wrapper.eq(SakuraAiVoiceLibrary::getDelFlag, query.getDelFlag());
            }
            if (StrUtil.isNotBlank(query.getVoiceName()))
            {
                wrapper.like(SakuraAiVoiceLibrary::getVoiceName, query.getVoiceName());
            }
            if (StrUtil.isNotBlank(query.getVoiceCode()))
            {
                wrapper.like(SakuraAiVoiceLibrary::getVoiceCode, query.getVoiceCode());
            }
        }
        // 稳定排序
        wrapper.orderByDesc(SakuraAiVoiceLibrary::getSortOrder).orderByDesc(SakuraAiVoiceLibrary::getId);
        return this.list(wrapper);
    }

    @Override
    public int insertSakuraAiVoiceLibrary(SakuraAiVoiceLibrary voiceLibrary)
    {
        voiceLibrary.setCreateTime(DateUtils.getNowDate());
        return this.save(voiceLibrary) ? 1 : 0;
    }

    @Override
    public int updateSakuraAiVoiceLibrary(SakuraAiVoiceLibrary voiceLibrary)
    {
        voiceLibrary.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(voiceLibrary) ? 1 : 0;
    }

    @Override
    public int deleteSakuraAiVoiceLibraryByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        boolean ok = this.update(Wrappers.<SakuraAiVoiceLibrary>lambdaUpdate()
                .in(SakuraAiVoiceLibrary::getId, Arrays.asList(ids))
                .eq(SakuraAiVoiceLibrary::getDelFlag, DEL_FLAG_NORMAL)
                .set(SakuraAiVoiceLibrary::getDelFlag, DEL_FLAG_DELETED)
                .set(SakuraAiVoiceLibrary::getUpdateTime, DateUtils.getNowDate()));
        return ok ? 1 : 0;
    }

    @Override
    public int deleteSakuraAiVoiceLibraryById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        SakuraAiVoiceLibrary update = new SakuraAiVoiceLibrary();
        update.setId(id);
        update.setDelFlag(DEL_FLAG_DELETED);
        update.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(update) ? 1 : 0;
    }
}
