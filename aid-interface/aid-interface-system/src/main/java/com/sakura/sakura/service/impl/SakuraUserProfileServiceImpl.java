package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraUserProfileMapper;
import com.sakura.sakura.domain.SakuraUserProfile;
import com.sakura.sakura.domain.vo.SakuraUserProfileVo;
import com.sakura.sakura.service.ISakuraUserProfileService;

/**
 * 用户扩展信息Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraUserProfileServiceImpl extends ServiceImpl<SakuraUserProfileMapper, SakuraUserProfile> implements ISakuraUserProfileService
{
    @Autowired
    private SakuraUserProfileMapper sakuraUserProfileMapper;

    /**
     * 查询用户扩展信息
     *
     * @param id 用户扩展信息主键
     * @return 用户扩展信息
     */
    @Override
    public SakuraUserProfile selectSakuraUserProfileById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询用户扩展信息列表
     *
     * @param sakuraUserProfile 用户扩展信息
     * @return 用户扩展信息
     */
    @Override
    public List<SakuraUserProfile> selectSakuraUserProfileList(SakuraUserProfile sakuraUserProfile)
    {
        LambdaQueryWrapper<SakuraUserProfile> wrapper = Wrappers.lambdaQuery();
        if (sakuraUserProfile != null)
        {
            if (sakuraUserProfile.getUserId() != null)
            {
                wrapper.eq(SakuraUserProfile::getUserId, sakuraUserProfile.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraUserProfile.getIsReal()))
            {
                wrapper.eq(SakuraUserProfile::getIsReal, sakuraUserProfile.getIsReal());
            }
            if (StrUtil.isNotBlank(sakuraUserProfile.getRealName()))
            {
                wrapper.like(SakuraUserProfile::getRealName, sakuraUserProfile.getRealName());
            }
            if (StrUtil.isNotBlank(sakuraUserProfile.getMemberLevel()))
            {
                wrapper.eq(SakuraUserProfile::getMemberLevel, sakuraUserProfile.getMemberLevel());
            }
        }
        wrapper.orderByDesc(SakuraUserProfile::getId);
        return this.list(wrapper);
    }

    /**
     * 新增用户扩展信息
     *
     * @param sakuraUserProfile 用户扩展信息
     * @return 结果
     */
    @Override
    public int insertSakuraUserProfile(SakuraUserProfile sakuraUserProfile)
    {
        sakuraUserProfile.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraUserProfile) ? 1 : 0;
    }

    /**
     * 修改用户扩展信息
     *
     * @param sakuraUserProfile 用户扩展信息
     * @return 结果
     */
    @Override
    public int updateSakuraUserProfile(SakuraUserProfile sakuraUserProfile)
    {
        sakuraUserProfile.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraUserProfile) ? 1 : 0;
    }

    /**
     * 批量删除用户扩展信息
     *
     * @param ids 需要删除的用户扩展信息主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserProfileByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除用户扩展信息信息
     *
     * @param id 用户扩展信息主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserProfileById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }

    /**
     * 按 userId 查询用户扩展信息
     */
    @Override
    public SakuraUserProfile getByUserId(Long userId)
    {
        if (userId == null)
        {
            return null;
        }
        LambdaQueryWrapper<SakuraUserProfile> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraUserProfile::getUserId, userId).last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * 按 userId 精简查询用户账户积分信息。
     */
    @Override
    public SakuraUserProfile getAccountBalanceByUserId(Long userId)
    {
        if (Objects.isNull(userId))
        {
            return null;
        }
        // 余额快捷接口只需要账户相关字段，后续新增字段时需同步确认是否需要返回
        LambdaQueryWrapper<SakuraUserProfile> wrapper = Wrappers.lambdaQuery();
        wrapper.select(SakuraUserProfile::getBalance,
                        SakuraUserProfile::getFrozenBalance,
                        SakuraUserProfile::getTotalRecharge,
                        SakuraUserProfile::getTotalConsumption)
                .eq(SakuraUserProfile::getUserId, userId)
                .eq(SakuraUserProfile::getDelFlag, "0")
                .last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * 关联 sys_user 查询用户聚合信息列表（后台用户管理）
     */
    @Override
    public List<SakuraUserProfileVo> selectUserProfileVoList(SakuraUserProfileVo query)
    {
        // 联表查询昵称/手机号/状态等基础信息，分页由 Controller 的 startPage 统一处理
        return sakuraUserProfileMapper.selectUserProfileVoList(query);
    }
}
