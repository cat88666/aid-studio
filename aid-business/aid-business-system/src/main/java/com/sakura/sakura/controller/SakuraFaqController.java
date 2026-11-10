package com.sakura.sakura.controller;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakura.sakura.domain.SakuraFaq;
import com.sakura.sakura.service.ISakuraFaqService;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.core.page.TableDataInfo;
import com.sakura.common.enums.BusinessType;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.poi.ExcelUtil;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 常见问题（FAQ）Controller（后台管理端）
 *
 * @author 视觉AID
 */
@Slf4j
@RestController
@RequestMapping("/sakura/faq")
public class SakuraFaqController extends BaseController
{
    @Autowired
    private ISakuraFaqService sakuraFaqService;

    /**
     * 查询常见问题列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:faq:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraFaq sakuraFaq)
    {
        startPage();
        List<SakuraFaq> list = sakuraFaqService.selectSakuraFaqList(sakuraFaq);
        return getDataTable(list);
    }

    /**
     * 导出常见问题列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:faq:export')")
    @Log(title = "常见问题", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraFaq sakuraFaq)
    {
        List<SakuraFaq> list = sakuraFaqService.selectSakuraFaqList(sakuraFaq);
        ExcelUtil<SakuraFaq> util = new ExcelUtil<SakuraFaq>(SakuraFaq.class);
        util.exportExcel(response, list, "常见问题数据");
    }

    /**
     * 获取常见问题详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:faq:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraFaqService.selectSakuraFaqById(id));
    }

    /**
     * 新增常见问题
     */
    @PreAuthorize("@ss.hasPermi('sakura:faq:add')")
    @Log(title = "常见问题", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraFaq sakuraFaq)
    {
        applyInsertDefaults(sakuraFaq);
        validate(sakuraFaq);
        return toAjax(sakuraFaqService.insertSakuraFaq(sakuraFaq));
    }

    /**
     * 修改常见问题
     */
    @PreAuthorize("@ss.hasPermi('sakura:faq:edit')")
    @Log(title = "常见问题", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraFaq sakuraFaq)
    {
        if (Objects.isNull(sakuraFaq) || Objects.isNull(sakuraFaq.getId()))
        {
            log.error("修改常见问题失败：主键为空");
            throw new ServiceException("参数不能为空");
        }
        validate(sakuraFaq);
        return toAjax(sakuraFaqService.updateSakuraFaq(sakuraFaq));
    }

    /**
     * 删除常见问题
     */
    @PreAuthorize("@ss.hasPermi('sakura:faq:remove')")
    @Log(title = "常见问题", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        log.info("删除常见问题, operator={}, ids={}", getUsername(), Arrays.toString(ids));
        return toAjax(sakuraFaqService.deleteSakuraFaqByIds(ids));
    }

    /**
     * 新增时填充默认值（仅作用于新增，避免编辑场景静默覆盖已有字段）：
     * 状态缺省"显示"、排序缺省 0、浏览量缺省 0、发布时间缺省当前时间。
     *
     * @param sakuraFaq 常见问题
     */
    private void applyInsertDefaults(SakuraFaq sakuraFaq)
    {
        if (Objects.isNull(sakuraFaq))
        {
            return;
        }
        if (StrUtil.isBlank(sakuraFaq.getStatus()))
        {
            sakuraFaq.setStatus("0");
        }
        if (Objects.isNull(sakuraFaq.getSortOrder()))
        {
            sakuraFaq.setSortOrder(0);
        }
        if (Objects.isNull(sakuraFaq.getViewCount()))
        {
            sakuraFaq.setViewCount(0L);
        }
        if (Objects.isNull(sakuraFaq.getPublishTime()))
        {
            sakuraFaq.setPublishTime(new Date());
        }
    }

    /**
     * 入参合法性校验（纯校验，不修改入参）：标题、内容必填。
     *
     * @param sakuraFaq 常见问题
     */
    private void validate(SakuraFaq sakuraFaq)
    {
        if (Objects.isNull(sakuraFaq))
        {
            throw new ServiceException("参数不能为空");
        }
        if (StrUtil.isBlank(sakuraFaq.getTitle()))
        {
            throw new ServiceException("标题不能为空");
        }
        if (StrUtil.isBlank(sakuraFaq.getContent()))
        {
            throw new ServiceException("内容不能为空");
        }
    }
}
