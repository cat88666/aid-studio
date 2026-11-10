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
import com.sakura.sakura.mapper.SakuraStoryboardMapper;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.service.ISakuraStoryboardService;

/**
 * 分镜时间轴主Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraStoryboardServiceImpl extends ServiceImpl<SakuraStoryboardMapper, SakuraStoryboard> implements ISakuraStoryboardService
{
    @Autowired
    private SakuraStoryboardMapper sakuraStoryboardMapper;

    /**
     * 查询分镜时间轴主
     *
     * @param id 分镜时间轴主主键
     * @return 分镜时间轴主
     */
    @Override
    public SakuraStoryboard selectSakuraStoryboardById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询分镜时间轴主列表
     *
     * @param sakuraStoryboard 分镜时间轴主
     * @return 分镜时间轴主
     */
    @Override
    public List<SakuraStoryboard> selectSakuraStoryboardList(SakuraStoryboard sakuraStoryboard)
    {
        LambdaQueryWrapper<SakuraStoryboard> wrapper = Wrappers.lambdaQuery();
        if (sakuraStoryboard != null)
        {
            if (sakuraStoryboard.getProjectId() != null)
            {
                wrapper.eq(SakuraStoryboard::getProjectId, sakuraStoryboard.getProjectId());
            }
            if (sakuraStoryboard.getEpisodeId() != null)
            {
                wrapper.eq(SakuraStoryboard::getEpisodeId, sakuraStoryboard.getEpisodeId());
            }
            if (sakuraStoryboard.getSourceSceneId() != null)
            {
                wrapper.eq(SakuraStoryboard::getSourceSceneId, sakuraStoryboard.getSourceSceneId());
            }
            if (sakuraStoryboard.getUserId() != null)
            {
                wrapper.eq(SakuraStoryboard::getUserId, sakuraStoryboard.getUserId());
            }
            if (sakuraStoryboard.getBatchId() != null)
            {
                wrapper.eq(SakuraStoryboard::getBatchId, sakuraStoryboard.getBatchId());
            }
            if (StrUtil.isNotBlank(sakuraStoryboard.getSourceSceneCode()))
            {
                wrapper.eq(SakuraStoryboard::getSourceSceneCode, sakuraStoryboard.getSourceSceneCode());
            }
            if (StrUtil.isNotBlank(sakuraStoryboard.getTitle()))
            {
                wrapper.like(SakuraStoryboard::getTitle, sakuraStoryboard.getTitle());
            }
        }
        wrapper.orderByAsc(SakuraStoryboard::getProjectId)
                .orderByAsc(SakuraStoryboard::getEpisodeId)
                .orderByAsc(SakuraStoryboard::getSortOrder)
                .orderByAsc(SakuraStoryboard::getId);
        return this.list(wrapper);
    }

    /**
     * 新增分镜时间轴主
     *
     * @param sakuraStoryboard 分镜时间轴主
     * @return 结果
     */
    @Override
    public int insertSakuraStoryboard(SakuraStoryboard sakuraStoryboard)
    {
        sakuraStoryboard.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraStoryboard) ? 1 : 0;
    }

    /**
     * 修改分镜时间轴主
     *
     * @param sakuraStoryboard 分镜时间轴主
     * @return 结果
     */
    @Override
    public int updateSakuraStoryboard(SakuraStoryboard sakuraStoryboard)
    {
        sakuraStoryboard.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraStoryboard) ? 1 : 0;
    }

    /**
     * 批量删除分镜时间轴主
     *
     * @param ids 需要删除的分镜时间轴主主键
     * @return 结果
     */
    @Override
    public int deleteSakuraStoryboardByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除分镜时间轴主信息
     *
     * @param id 分镜时间轴主主键
     * @return 结果
     */
    @Override
    public int deleteSakuraStoryboardById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
