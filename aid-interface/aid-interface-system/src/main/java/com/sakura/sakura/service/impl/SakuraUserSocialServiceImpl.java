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
import com.sakura.sakura.mapper.SakuraUserSocialMapper;
import com.sakura.sakura.domain.SakuraUserSocial;
import com.sakura.sakura.service.ISakuraUserSocialService;

/**
 * 用户第三方登录授权Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraUserSocialServiceImpl extends ServiceImpl<SakuraUserSocialMapper, SakuraUserSocial> implements ISakuraUserSocialService
{
    @Autowired
    private SakuraUserSocialMapper sakuraUserSocialMapper;

    /**
     * 查询用户第三方登录授权
     *
     * @param id 用户第三方登录授权主键
     * @return 用户第三方登录授权
     */
    @Override
    public SakuraUserSocial selectSakuraUserSocialById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询用户第三方登录授权列表
     *
     * @param sakuraUserSocial 用户第三方登录授权
     * @return 用户第三方登录授权
     */
    @Override
    public List<SakuraUserSocial> selectSakuraUserSocialList(SakuraUserSocial sakuraUserSocial)
    {
        LambdaQueryWrapper<SakuraUserSocial> wrapper = Wrappers.lambdaQuery();
        if (sakuraUserSocial != null)
        {
            if (sakuraUserSocial.getUserId() != null)
            {
                wrapper.eq(SakuraUserSocial::getUserId, sakuraUserSocial.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraUserSocial.getPlatformSource()))
            {
                wrapper.eq(SakuraUserSocial::getPlatformSource, sakuraUserSocial.getPlatformSource());
            }
            if (StrUtil.isNotBlank(sakuraUserSocial.getOpenid()))
            {
                wrapper.eq(SakuraUserSocial::getOpenid, sakuraUserSocial.getOpenid());
            }
            if (StrUtil.isNotBlank(sakuraUserSocial.getUnionid()))
            {
                wrapper.eq(SakuraUserSocial::getUnionid, sakuraUserSocial.getUnionid());
            }
        }
        wrapper.orderByDesc(SakuraUserSocial::getId);
        return this.list(wrapper);
    }

    /**
     * 新增用户第三方登录授权
     *
     * @param sakuraUserSocial 用户第三方登录授权
     * @return 结果
     */
    @Override
    public int insertSakuraUserSocial(SakuraUserSocial sakuraUserSocial)
    {
        sakuraUserSocial.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraUserSocial) ? 1 : 0;
    }

    /**
     * 修改用户第三方登录授权
     *
     * @param sakuraUserSocial 用户第三方登录授权
     * @return 结果
     */
    @Override
    public int updateSakuraUserSocial(SakuraUserSocial sakuraUserSocial)
    {
        sakuraUserSocial.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraUserSocial) ? 1 : 0;
    }

    /**
     * 批量删除用户第三方登录授权
     *
     * @param ids 需要删除的用户第三方登录授权主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserSocialByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除用户第三方登录授权信息
     *
     * @param id 用户第三方登录授权主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserSocialById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
