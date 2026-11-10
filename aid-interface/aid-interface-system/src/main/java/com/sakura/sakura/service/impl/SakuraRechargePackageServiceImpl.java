package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import com.sakura.sakura.mapper.SakuraRechargePackageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraRechargePackage;
import com.sakura.sakura.service.ISakuraRechargePackageService;

/**
 * 充值套餐配置Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraRechargePackageServiceImpl extends ServiceImpl<SakuraRechargePackageMapper, SakuraRechargePackage> implements ISakuraRechargePackageService
{
    @Autowired
    private SakuraRechargePackageMapper sakuraRechargePackageMapper;

    /**
     * 查询充值套餐配置
     *
     * @param id 充值套餐配置主键
     * @return 充值套餐配置
     */
    @Override
    public SakuraRechargePackage selectSakuraRechargePackageById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询充值套餐配置列表
     *
     * @param sakuraRechargePackage 充值套餐配置
     * @return 充值套餐配置
     */
    @Override
    public List<SakuraRechargePackage> selectSakuraRechargePackageList(SakuraRechargePackage sakuraRechargePackage)
    {
        LambdaQueryWrapper<SakuraRechargePackage> wrapper = Wrappers.lambdaQuery();
        if (sakuraRechargePackage != null)
        {
            if (StrUtil.isNotBlank(sakuraRechargePackage.getPackageName()))
            {
                wrapper.like(SakuraRechargePackage::getPackageName, sakuraRechargePackage.getPackageName());
            }
            if (StrUtil.isNotBlank(sakuraRechargePackage.getStatus()))
            {
                wrapper.eq(SakuraRechargePackage::getStatus, sakuraRechargePackage.getStatus());
            }
        }
        wrapper.orderByAsc(SakuraRechargePackage::getSortOrder).orderByDesc(SakuraRechargePackage::getId);
        return this.list(wrapper);
    }

    /**
     * 新增充值套餐配置
     *
     * @param sakuraRechargePackage 充值套餐配置
     * @return 结果
     */
    @Override
    public int insertSakuraRechargePackage(SakuraRechargePackage sakuraRechargePackage)
    {
        sakuraRechargePackage.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraRechargePackage) ? 1 : 0;
    }

    /**
     * 修改充值套餐配置
     *
     * @param sakuraRechargePackage 充值套餐配置
     * @return 结果
     */
    @Override
    public int updateSakuraRechargePackage(SakuraRechargePackage sakuraRechargePackage)
    {
        sakuraRechargePackage.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraRechargePackage) ? 1 : 0;
    }

    /**
     * 批量删除充值套餐配置
     *
     * @param ids 需要删除的充值套餐配置主键
     * @return 结果
     */
    @Override
    public int deleteSakuraRechargePackageByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除充值套餐配置信息
     *
     * @param id 充值套餐配置主键
     * @return 结果
     */
    @Override
    public int deleteSakuraRechargePackageById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
