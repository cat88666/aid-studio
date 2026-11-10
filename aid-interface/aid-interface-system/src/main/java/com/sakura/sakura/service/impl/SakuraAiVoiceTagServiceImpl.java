package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraAiVoiceTag;
import com.sakura.sakura.mapper.SakuraAiVoiceTagMapper;
import com.sakura.sakura.service.ISakuraAiVoiceTagService;
import com.sakura.common.utils.DateUtils;
import org.springframework.stereotype.Service;

/**
 * AI音色标签字典 Service 实现（基础 CRUD）
 * 本类仅承载裸 CRUD，业务校验（唯一键冲突、tag_type 合法性、字段长度等）
 * 由 {@code aid-interface-main} 的业务 Service 负责。
 *
 * @author 视觉AID
 */
@Service
public class SakuraAiVoiceTagServiceImpl extends ServiceImpl<SakuraAiVoiceTagMapper, SakuraAiVoiceTag> implements ISakuraAiVoiceTagService
{
    /** 删除标志：存在 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：已删除 */
    private static final String DEL_FLAG_DELETED = "2";

    @Override
    public SakuraAiVoiceTag selectSakuraAiVoiceTagById(Long id)
    {
        return this.getById(id);
    }

    @Override
    public List<SakuraAiVoiceTag> selectSakuraAiVoiceTagList(SakuraAiVoiceTag query)
    {
        LambdaQueryWrapper<SakuraAiVoiceTag> wrapper = Wrappers.lambdaQuery();
        if (query != null)
        {
            if (StrUtil.isNotBlank(query.getTagType()))
            {
                wrapper.eq(SakuraAiVoiceTag::getTagType, query.getTagType());
            }
            if (StrUtil.isNotBlank(query.getTagCode()))
            {
                wrapper.like(SakuraAiVoiceTag::getTagCode, query.getTagCode());
            }
            if (StrUtil.isNotBlank(query.getTagName()))
            {
                wrapper.like(SakuraAiVoiceTag::getTagName, query.getTagName());
            }
            if (StrUtil.isNotBlank(query.getStatus()))
            {
                wrapper.eq(SakuraAiVoiceTag::getStatus, query.getStatus());
            }
            if (StrUtil.isNotBlank(query.getDelFlag()))
            {
                wrapper.eq(SakuraAiVoiceTag::getDelFlag, query.getDelFlag());
            }
        }
        // 稳定排序：sort_order DESC, id DESC
        wrapper.orderByDesc(SakuraAiVoiceTag::getSortOrder).orderByDesc(SakuraAiVoiceTag::getId);
        return this.list(wrapper);
    }

    @Override
    public int insertSakuraAiVoiceTag(SakuraAiVoiceTag voiceTag)
    {
        voiceTag.setCreateTime(DateUtils.getNowDate());
        return this.save(voiceTag) ? 1 : 0;
    }

    @Override
    public int updateSakuraAiVoiceTag(SakuraAiVoiceTag voiceTag)
    {
        voiceTag.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(voiceTag) ? 1 : 0;
    }

    @Override
    public int deleteSakuraAiVoiceTagByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        boolean ok = this.update(Wrappers.<SakuraAiVoiceTag>lambdaUpdate()
                .in(SakuraAiVoiceTag::getId, Arrays.asList(ids))
                .eq(SakuraAiVoiceTag::getDelFlag, DEL_FLAG_NORMAL)
                .set(SakuraAiVoiceTag::getDelFlag, DEL_FLAG_DELETED)
                .set(SakuraAiVoiceTag::getUpdateTime, DateUtils.getNowDate()));
        return ok ? 1 : 0;
    }

    @Override
    public int deleteSakuraAiVoiceTagById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        SakuraAiVoiceTag update = new SakuraAiVoiceTag();
        update.setId(id);
        update.setDelFlag(DEL_FLAG_DELETED);
        update.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(update) ? 1 : 0;
    }
}
