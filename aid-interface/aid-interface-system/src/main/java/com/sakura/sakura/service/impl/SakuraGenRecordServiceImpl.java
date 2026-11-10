package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraGenRecordMapper;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.service.ISakuraGenRecordService;

/**
 * AI生图/生视频抽卡记录Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraGenRecordServiceImpl extends ServiceImpl<SakuraGenRecordMapper, SakuraGenRecord> implements ISakuraGenRecordService
{
    @Autowired
    private SakuraGenRecordMapper sakuraGenRecordMapper;

    /**
     * 查询AI生图/生视频抽卡记录
     *
     * @param id AI生图/生视频抽卡记录主键
     * @return AI生图/生视频抽卡记录
     */
    @Override
    public SakuraGenRecord selectSakuraGenRecordById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询AI生图/生视频抽卡记录列表
     *
     * @param sakuraGenRecord AI生图/生视频抽卡记录
     * @return AI生图/生视频抽卡记录
     */
    @Override
    public List<SakuraGenRecord> selectSakuraGenRecordList(SakuraGenRecord sakuraGenRecord)
    {
        LambdaQueryWrapper<SakuraGenRecord> wrapper = Wrappers.lambdaQuery();
        if (sakuraGenRecord != null)
        {
            if (sakuraGenRecord.getUserId() != null)
            {
                wrapper.eq(SakuraGenRecord::getUserId, sakuraGenRecord.getUserId());
            }
            if (sakuraGenRecord.getProjectId() != null)
            {
                wrapper.eq(SakuraGenRecord::getProjectId, sakuraGenRecord.getProjectId());
            }
            if (sakuraGenRecord.getEpisodeId() != null)
            {
                wrapper.eq(SakuraGenRecord::getEpisodeId, sakuraGenRecord.getEpisodeId());
            }
            if (sakuraGenRecord.getStoryboardId() != null)
            {
                wrapper.eq(SakuraGenRecord::getStoryboardId, sakuraGenRecord.getStoryboardId());
            }
            if (StrUtil.isNotBlank(sakuraGenRecord.getGenType()))
            {
                wrapper.eq(SakuraGenRecord::getGenType, sakuraGenRecord.getGenType());
            }
            if (StrUtil.isNotBlank(sakuraGenRecord.getTaskId()))
            {
                wrapper.eq(SakuraGenRecord::getTaskId, sakuraGenRecord.getTaskId());
            }
            if (sakuraGenRecord.getStatus() != null)
            {
                wrapper.eq(SakuraGenRecord::getStatus, sakuraGenRecord.getStatus());
            }
            if (sakuraGenRecord.getModelId() != null)
            {
                wrapper.eq(SakuraGenRecord::getModelId, sakuraGenRecord.getModelId());
            }
            if (sakuraGenRecord.getIsSelected() != null)
            {
                wrapper.eq(SakuraGenRecord::getIsSelected, sakuraGenRecord.getIsSelected());
            }
        }
        wrapper.orderByDesc(SakuraGenRecord::getId);
        return this.list(wrapper);
    }

    /**
     * 新增AI生图/生视频抽卡记录
     *
     * @param sakuraGenRecord AI生图/生视频抽卡记录
     * @return 结果
     */
    @Override
    public int insertSakuraGenRecord(SakuraGenRecord sakuraGenRecord)
    {
        sakuraGenRecord.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraGenRecord) ? 1 : 0;
    }

    /**
     * 修改AI生图/生视频抽卡记录
     *
     * @param sakuraGenRecord AI生图/生视频抽卡记录
     * @return 结果
     */
    @Override
    public int updateSakuraGenRecord(SakuraGenRecord sakuraGenRecord)
    {
        sakuraGenRecord.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraGenRecord) ? 1 : 0;
    }

    /**
     * 批量删除AI生图/生视频抽卡记录
     *
     * @param ids 需要删除的AI生图/生视频抽卡记录主键
     * @return 结果
     */
    @Override
    public int deleteSakuraGenRecordByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除AI生图/生视频抽卡记录信息
     *
     * @param id AI生图/生视频抽卡记录主键
     * @return 结果
     */
    @Override
    public int deleteSakuraGenRecordById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
