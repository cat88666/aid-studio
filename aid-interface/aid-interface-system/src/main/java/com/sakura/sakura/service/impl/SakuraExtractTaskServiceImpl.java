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
import com.sakura.sakura.mapper.SakuraExtractTaskMapper;
import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.service.ISakuraExtractTaskService;

/**
 * 资产提取任务Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraExtractTaskServiceImpl extends ServiceImpl<SakuraExtractTaskMapper, SakuraExtractTask> implements ISakuraExtractTaskService
{
    @Autowired
    private SakuraExtractTaskMapper sakuraExtractTaskMapper;

    /**
     * 查询资产提取任务
     *
     * @param id 资产提取任务主键
     * @return 资产提取任务
     */
    @Override
    public SakuraExtractTask selectSakuraExtractTaskById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询资产提取任务列表
     *
     * @param sakuraExtractTask 资产提取任务
     * @return 资产提取任务
     */
    @Override
    public List<SakuraExtractTask> selectSakuraExtractTaskList(SakuraExtractTask sakuraExtractTask)
    {
        LambdaQueryWrapper<SakuraExtractTask> wrapper = Wrappers.lambdaQuery();
        if (sakuraExtractTask != null)
        {
            if (sakuraExtractTask.getProjectId() != null)
            {
                wrapper.eq(SakuraExtractTask::getProjectId, sakuraExtractTask.getProjectId());
            }
            if (sakuraExtractTask.getEpisodeId() != null)
            {
                wrapper.eq(SakuraExtractTask::getEpisodeId, sakuraExtractTask.getEpisodeId());
            }
            if (sakuraExtractTask.getUserId() != null)
            {
                wrapper.eq(SakuraExtractTask::getUserId, sakuraExtractTask.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraExtractTask.getTaskType()))
            {
                wrapper.eq(SakuraExtractTask::getTaskType, sakuraExtractTask.getTaskType());
            }
            if (StrUtil.isNotBlank(sakuraExtractTask.getStatus()))
            {
                wrapper.eq(SakuraExtractTask::getStatus, sakuraExtractTask.getStatus());
            }
            if (StrUtil.isNotBlank(sakuraExtractTask.getModelCode()))
            {
                wrapper.eq(SakuraExtractTask::getModelCode, sakuraExtractTask.getModelCode());
            }
            if (StrUtil.isNotBlank(sakuraExtractTask.getBillingStatus()))
            {
                wrapper.eq(SakuraExtractTask::getBillingStatus, sakuraExtractTask.getBillingStatus());
            }
        }
        wrapper.orderByDesc(SakuraExtractTask::getId);
        return this.list(wrapper);
    }

    /**
     * 新增资产提取任务
     *
     * @param sakuraExtractTask 资产提取任务
     * @return 结果
     */
    @Override
    public int insertSakuraExtractTask(SakuraExtractTask sakuraExtractTask)
    {
        sakuraExtractTask.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraExtractTask) ? 1 : 0;
    }

    /**
     * 修改资产提取任务
     *
     * @param sakuraExtractTask 资产提取任务
     * @return 结果
     */
    @Override
    public int updateSakuraExtractTask(SakuraExtractTask sakuraExtractTask)
    {
        sakuraExtractTask.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraExtractTask) ? 1 : 0;
    }

    /**
     * 批量删除资产提取任务
     *
     * @param ids 需要删除的资产提取任务主键
     * @return 结果
     */
    @Override
    public int deleteSakuraExtractTaskByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除资产提取任务信息
     *
     * @param id 资产提取任务主键
     * @return 结果
     */
    @Override
    public int deleteSakuraExtractTaskById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
