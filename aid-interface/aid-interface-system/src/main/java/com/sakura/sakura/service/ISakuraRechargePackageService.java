package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraRechargePackage;

/**
 * 充值套餐配置Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraRechargePackageService extends IService<SakuraRechargePackage>
{
    /**
     * 查询充值套餐配置
     *
     * @param id 充值套餐配置主键
     * @return 充值套餐配置
     */
    public SakuraRechargePackage selectSakuraRechargePackageById(Long id);

    /**
     * 查询充值套餐配置列表
     *
     * @param sakuraRechargePackage 充值套餐配置
     * @return 充值套餐配置集合
     */
    public List<SakuraRechargePackage> selectSakuraRechargePackageList(SakuraRechargePackage sakuraRechargePackage);

    /**
     * 新增充值套餐配置
     *
     * @param sakuraRechargePackage 充值套餐配置
     * @return 结果
     */
    public int insertSakuraRechargePackage(SakuraRechargePackage sakuraRechargePackage);

    /**
     * 修改充值套餐配置
     *
     * @param sakuraRechargePackage 充值套餐配置
     * @return 结果
     */
    public int updateSakuraRechargePackage(SakuraRechargePackage sakuraRechargePackage);

    /**
     * 批量删除充值套餐配置
     *
     * @param ids 需要删除的充值套餐配置主键集合
     * @return 结果
     */
    public int deleteSakuraRechargePackageByIds(Long[] ids);

    /**
     * 删除充值套餐配置信息
     *
     * @param id 充值套餐配置主键
     * @return 结果
     */
    public int deleteSakuraRechargePackageById(Long id);
}
