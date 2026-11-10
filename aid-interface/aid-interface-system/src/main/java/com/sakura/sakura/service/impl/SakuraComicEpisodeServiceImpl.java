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
import com.sakura.sakura.mapper.SakuraComicEpisodeMapper;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.service.ISakuraComicEpisodeService;

/**
 * 剧集信息Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraComicEpisodeServiceImpl extends ServiceImpl<SakuraComicEpisodeMapper, SakuraComicEpisode> implements ISakuraComicEpisodeService
{
    @Autowired
    private SakuraComicEpisodeMapper sakuraComicEpisodeMapper;

    /**
     * 查询剧集信息
     *
     * @param id 剧集信息主键
     * @return 剧集信息
     */
    @Override
    public SakuraComicEpisode selectSakuraComicEpisodeById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询剧集信息列表
     *
     * @param sakuraComicEpisode 剧集信息
     * @return 剧集信息
     */
    @Override
    public List<SakuraComicEpisode> selectSakuraComicEpisodeList(SakuraComicEpisode sakuraComicEpisode)
    {
        LambdaQueryWrapper<SakuraComicEpisode> wrapper = Wrappers.lambdaQuery();
        if (sakuraComicEpisode != null)
        {
            if (sakuraComicEpisode.getProjectId() != null)
            {
                wrapper.eq(SakuraComicEpisode::getProjectId, sakuraComicEpisode.getProjectId());
            }
            if (sakuraComicEpisode.getEpisodeNo() != null)
            {
                wrapper.eq(SakuraComicEpisode::getEpisodeNo, sakuraComicEpisode.getEpisodeNo());
            }
            if (StrUtil.isNotBlank(sakuraComicEpisode.getComicTitle()))
            {
                wrapper.like(SakuraComicEpisode::getComicTitle, sakuraComicEpisode.getComicTitle());
            }
            if (sakuraComicEpisode.getUserId() != null)
            {
                wrapper.eq(SakuraComicEpisode::getUserId, sakuraComicEpisode.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraComicEpisode.getGenMode()))
            {
                wrapper.eq(SakuraComicEpisode::getGenMode, sakuraComicEpisode.getGenMode());
            }
            if (StrUtil.isNotBlank(sakuraComicEpisode.getCreationMode()))
            {
                wrapper.eq(SakuraComicEpisode::getCreationMode, sakuraComicEpisode.getCreationMode());
            }
            if (sakuraComicEpisode.getCurrentStep() != null)
            {
                wrapper.eq(SakuraComicEpisode::getCurrentStep, sakuraComicEpisode.getCurrentStep());
            }
            if (sakuraComicEpisode.getStatus() != null)
            {
                wrapper.eq(SakuraComicEpisode::getStatus, sakuraComicEpisode.getStatus());
            }
        }
        wrapper.orderByAsc(SakuraComicEpisode::getProjectId).orderByAsc(SakuraComicEpisode::getEpisodeNo);
        return this.list(wrapper);
    }

    /**
     * 新增剧集信息
     *
     * @param sakuraComicEpisode 剧集信息
     * @return 结果
     */
    @Override
    public int insertSakuraComicEpisode(SakuraComicEpisode sakuraComicEpisode)
    {
        sakuraComicEpisode.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraComicEpisode) ? 1 : 0;
    }

    /**
     * 修改剧集信息
     *
     * @param sakuraComicEpisode 剧集信息
     * @return 结果
     */
    @Override
    public int updateSakuraComicEpisode(SakuraComicEpisode sakuraComicEpisode)
    {
        sakuraComicEpisode.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraComicEpisode) ? 1 : 0;
    }

    /**
     * 批量删除剧集信息
     *
     * @param ids 需要删除的剧集信息主键
     * @return 结果
     */
    @Override
    public int deleteSakuraComicEpisodeByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除剧集信息信息
     *
     * @param id 剧集信息主键
     * @return 结果
     */
    @Override
    public int deleteSakuraComicEpisodeById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
