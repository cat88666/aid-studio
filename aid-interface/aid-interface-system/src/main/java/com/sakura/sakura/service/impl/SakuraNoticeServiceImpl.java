package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraNotice;
import com.sakura.sakura.mapper.SakuraNoticeMapper;
import com.sakura.sakura.service.ISakuraNoticeService;
import com.sakura.common.utils.DateUtils;
import com.sakura.common.utils.SecurityUtils;

import cn.hutool.core.util.StrUtil;

/**
 * C 端公告 Service 业务层处理（后台管理维护用）
 *
 * @author 视觉AID
 */
@Service
public class SakuraNoticeServiceImpl extends ServiceImpl<SakuraNoticeMapper, SakuraNotice> implements ISakuraNoticeService
{
    /**
     * 查询公告
     *
     * @param id 公告主键
     * @return 公告
     */
    @Override
    public SakuraNotice selectSakuraNoticeById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询公告列表
     *
     * @param sakuraNotice 公告（标题/类型/状态作为检索条件）
     * @return 公告集合
     */
    @Override
    public List<SakuraNotice> selectSakuraNoticeList(SakuraNotice sakuraNotice)
    {
        LambdaQueryWrapper<SakuraNotice> wrapper = Wrappers.lambdaQuery();
        if (Objects.nonNull(sakuraNotice))
        {
            // 标题模糊检索
            if (StrUtil.isNotBlank(sakuraNotice.getTitle()))
            {
                wrapper.like(SakuraNotice::getTitle, sakuraNotice.getTitle());
            }
            // 公告类型精确检索
            if (StrUtil.isNotBlank(sakuraNotice.getNoticeType()))
            {
                wrapper.eq(SakuraNotice::getNoticeType, sakuraNotice.getNoticeType());
            }
            // 是否视频精确检索
            if (StrUtil.isNotBlank(sakuraNotice.getIsVideo()))
            {
                wrapper.eq(SakuraNotice::getIsVideo, sakuraNotice.getIsVideo());
            }
            // 状态精确检索
            if (StrUtil.isNotBlank(sakuraNotice.getStatus()))
            {
                wrapper.eq(SakuraNotice::getStatus, sakuraNotice.getStatus());
            }
        }
        // 排序：置顶优先，其次 sort_order 升序，相同再按 id 倒序
        wrapper.orderByDesc(SakuraNotice::getIsTop)
                .orderByAsc(SakuraNotice::getSortOrder)
                .orderByDesc(SakuraNotice::getId);
        return this.list(wrapper);
    }

    /**
     * 新增公告
     *
     * @param sakuraNotice 公告
     * @return 结果
     */
    @Override
    public int insertSakuraNotice(SakuraNotice sakuraNotice)
    {
        // 填充创建时间与创建者
        sakuraNotice.setCreateTime(DateUtils.getNowDate());
        sakuraNotice.setCreateBy(SecurityUtils.getUsername());
        return this.save(sakuraNotice) ? 1 : 0;
    }

    /**
     * 修改公告
     *
     * @param sakuraNotice 公告
     * @return 结果
     */
    @Override
    public int updateSakuraNotice(SakuraNotice sakuraNotice)
    {
        // 填充更新时间与更新者
        sakuraNotice.setUpdateTime(DateUtils.getNowDate());
        sakuraNotice.setUpdateBy(SecurityUtils.getUsername());
        return this.updateById(sakuraNotice) ? 1 : 0;
    }

    /**
     * 批量删除公告
     *
     * @param ids 需要删除的公告主键
     * @return 结果
     */
    @Override
    public int deleteSakuraNoticeByIds(Long[] ids)
    {
        if (Objects.isNull(ids) || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除公告信息
     *
     * @param id 公告主键
     * @return 结果
     */
    @Override
    public int deleteSakuraNoticeById(Long id)
    {
        if (Objects.isNull(id))
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
