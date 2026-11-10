package com.sakura.sakura.controller;

import java.math.BigDecimal;
import java.util.List;

import com.sakura.sakura.domain.SakuraRechargePackage;
import com.sakura.sakura.service.ISakuraRechargePackageService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.enums.BusinessType;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 充值套餐配置Controller
 *
 * @author 视觉AID
 */
@Slf4j
@RestController
@RequestMapping("/sakura/rechargepackage")
public class SakuraRechargePackageController extends BaseController
{
    @Autowired
    private ISakuraRechargePackageService sakuraRechargePackageService;

    /**
     * 查询充值套餐配置列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:rechargepackage:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraRechargePackage sakuraRechargePackage)
    {
        startPage();
        List<SakuraRechargePackage> list = sakuraRechargePackageService.selectSakuraRechargePackageList(sakuraRechargePackage);
        return getDataTable(list);
    }

    /**
     * 导出充值套餐配置列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:rechargepackage:export')")
    @Log(title = "充值套餐配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraRechargePackage sakuraRechargePackage)
    {
        List<SakuraRechargePackage> list = sakuraRechargePackageService.selectSakuraRechargePackageList(sakuraRechargePackage);
        ExcelUtil<SakuraRechargePackage> util = new ExcelUtil<SakuraRechargePackage>(SakuraRechargePackage.class);
        util.exportExcel(response, list, "充值套餐配置数据");
    }

    /**
     * 获取充值套餐配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:rechargepackage:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraRechargePackageService.selectSakuraRechargePackageById(id));
    }

    /**
     * 新增充值套餐配置
     * 对套餐价格相关字段做基本合法性校验，防止"0 元套餐换百万积分"这类误配。
     */
    @PreAuthorize("@ss.hasPermi('sakura:rechargepackage:add')")
    @Log(title = "充值套餐配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraRechargePackage sakuraRechargePackage)
    {
        validatePricing(sakuraRechargePackage);
        log.warn("[AUDIT-PACKAGE] 新增套餐, operator={}, name={}, credits={}, payPrice={}, originalPrice={}",
                getUsername(), sakuraRechargePackage.getPackageName(), sakuraRechargePackage.getCredits(),
                sakuraRechargePackage.getPayPrice(), sakuraRechargePackage.getOriginalPrice());
        return toAjax(sakuraRechargePackageService.insertSakuraRechargePackage(sakuraRechargePackage));
    }

    /**
     * 修改充值套餐配置
     * 对价格 / 积分的变更打审计日志（old→new），便于事后追溯套餐价格改动。
     */
    @PreAuthorize("@ss.hasPermi('sakura:rechargepackage:edit')")
    @Log(title = "充值套餐配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraRechargePackage sakuraRechargePackage)
    {
        validatePricing(sakuraRechargePackage);
        if (sakuraRechargePackage != null && sakuraRechargePackage.getId() != null) {
            SakuraRechargePackage old = sakuraRechargePackageService.selectSakuraRechargePackageById(sakuraRechargePackage.getId());
            if (old != null) {
                log.warn("[AUDIT-PACKAGE] 修改套餐, operator={}, id={}, name: {} -> {}, credits: {} -> {}, "
                                + "payPrice: {} -> {}, originalPrice: {} -> {}, discount: {} -> {}",
                        getUsername(), old.getId(),
                        old.getPackageName(), sakuraRechargePackage.getPackageName(),
                        old.getCredits(), sakuraRechargePackage.getCredits(),
                        old.getPayPrice(), sakuraRechargePackage.getPayPrice(),
                        old.getOriginalPrice(), sakuraRechargePackage.getOriginalPrice(),
                        old.getDiscount(), sakuraRechargePackage.getDiscount());
            }
        }
        return toAjax(sakuraRechargePackageService.updateSakuraRechargePackage(sakuraRechargePackage));
    }

    /**
     * 删除充值套餐配置
     * 打审计日志，便于追踪套餐下架变动。
     */
    @PreAuthorize("@ss.hasPermi('sakura:rechargepackage:remove')")
    @Log(title = "充值套餐配置", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        log.warn("[AUDIT-PACKAGE] 删除套餐, operator={}, ids={}", getUsername(), java.util.Arrays.toString(ids));
        return toAjax(sakuraRechargePackageService.deleteSakuraRechargePackageByIds(ids));
    }

    /**
     * 套餐价格合法性校验。
     *  - payPrice / originalPrice 必须为正数
     *  - credits 必须为正整数
     *  - discount（0~1 折扣系数）上限 1
     * 拦截明显误配（例如 0 元对应 100 万积分）。
     */
    private void validatePricing(SakuraRechargePackage sakuraRechargePackage) {
        if (sakuraRechargePackage == null) {
            throw new com.sakura.common.exception.ServiceException("套餐参数不能为空");
        }
        if (sakuraRechargePackage.getPayPrice() == null
                || sakuraRechargePackage.getPayPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.sakura.common.exception.ServiceException("实付金额必须 > 0");
        }
        if (sakuraRechargePackage.getCredits() == null
                || sakuraRechargePackage.getCredits().compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.sakura.common.exception.ServiceException("积分必须 > 0");
        }
        if (sakuraRechargePackage.getOriginalPrice() != null
                && sakuraRechargePackage.getOriginalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.sakura.common.exception.ServiceException("原价必须 > 0");
        }
        if (sakuraRechargePackage.getDiscount() != null) {
            BigDecimal d = sakuraRechargePackage.getDiscount();
            if (d.compareTo(BigDecimal.ZERO) < 0 || d.compareTo(BigDecimal.ONE) > 0) {
                throw new com.sakura.common.exception.ServiceException("折扣系数必须在 [0,1]");
            }
        }
    }
}
