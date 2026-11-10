package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraGenAgentPool;
import com.sakura.sakura.mapper.SakuraGenAgentPoolMapper;
import com.sakura.sakura.service.ISakuraGenAgentPoolService;
import com.sakura.common.utils.DateUtils;
import com.sakura.common.utils.SecurityUtils;

/**
 * 生成链路智能体可选池Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraGenAgentPoolServiceImpl extends ServiceImpl<SakuraGenAgentPoolMapper, SakuraGenAgentPool>
        implements ISakuraGenAgentPoolService
{
    /** 删除标志：正常（未删除） */
    private static final String DEL_FLAG_NORMAL = "0";

    @Override
    public SakuraGenAgentPool selectSakuraGenAgentPoolById(Long id)
    {
        return this.getById(id);
    }

    @Override
    public List<SakuraGenAgentPool> selectSakuraGenAgentPoolList(SakuraGenAgentPool query)
    {
        LambdaQueryWrapper<SakuraGenAgentPool> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraGenAgentPool::getDelFlag, DEL_FLAG_NORMAL);
        if (query != null)
        {
            if (StrUtil.isNotBlank(query.getStep()))
            {
                wrapper.eq(SakuraGenAgentPool::getStep, query.getStep());
            }
            if (StrUtil.isNotBlank(query.getBizCategoryCode()))
            {
                wrapper.eq(SakuraGenAgentPool::getBizCategoryCode, query.getBizCategoryCode());
            }
            if (StrUtil.isNotBlank(query.getCreationMode()))
            {
                wrapper.eq(SakuraGenAgentPool::getCreationMode, query.getCreationMode());
            }
            if (StrUtil.isNotBlank(query.getScriptType()))
            {
                wrapper.eq(SakuraGenAgentPool::getScriptType, query.getScriptType());
            }
            if (StrUtil.isNotBlank(query.getStrategy()))
            {
                wrapper.eq(SakuraGenAgentPool::getStrategy, query.getStrategy());
            }
            if (StrUtil.isNotBlank(query.getAgentCode()))
            {
                wrapper.like(SakuraGenAgentPool::getAgentCode, query.getAgentCode());
            }
            if (StrUtil.isNotBlank(query.getStatus()))
            {
                wrapper.eq(SakuraGenAgentPool::getStatus, query.getStatus());
            }
        }
        wrapper.orderByAsc(SakuraGenAgentPool::getStep)
                .orderByAsc(SakuraGenAgentPool::getSortOrder)
                .orderByAsc(SakuraGenAgentPool::getId);
        return this.list(wrapper);
    }

    @Override
    public int insertSakuraGenAgentPool(SakuraGenAgentPool entity)
    {
        entity.setDelFlag(DEL_FLAG_NORMAL);
        entity.setCreateBy(currentUser());
        entity.setCreateTime(DateUtils.getNowDate());
        return this.save(entity) ? 1 : 0;
    }

    @Override
    public int updateSakuraGenAgentPool(SakuraGenAgentPool entity)
    {
        entity.setUpdateBy(currentUser());
        entity.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(entity) ? 1 : 0;
    }

    @Override
    public int deleteSakuraGenAgentPoolByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /** 当前操作者用户名，取不到回退 system */
    private String currentUser()
    {
        try
        {
            String name = SecurityUtils.getUsername();
            return StrUtil.isBlank(name) ? "system" : name;
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
