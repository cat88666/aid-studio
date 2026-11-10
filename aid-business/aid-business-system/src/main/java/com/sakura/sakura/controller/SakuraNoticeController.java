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

import com.sakura.sakura.domain.SakuraNotice;
import com.sakura.sakura.service.ISakuraNoticeService;
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
 * C 端公告 Controller（后台管理端）
 *
 * @author 视觉AID
 */
@Slf4j
@RestController
@RequestMapping("/sakura/notice")
public class SakuraNoticeController extends BaseController
{
    /** 标志位：否 */
    private static final String FLAG_NO = "0";
    /** 标志位：是 */
    private static final String FLAG_YES = "1";
    /** 公告类型：系统公告 */
    private static final String NOTICE_TYPE_SYSTEM = "system";
    /** 公告类型：活动公告 */
    private static final String NOTICE_TYPE_ACTIVITY = "activity";
    /** 公告类型：更新公告 */
    private static final String NOTICE_TYPE_UPDATE = "update";

    @Autowired
    private ISakuraNoticeService sakuraNoticeService;

    /**
     * 查询公告列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:notice:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraNotice sakuraNotice)
    {
        startPage();
        List<SakuraNotice> list = sakuraNoticeService.selectSakuraNoticeList(sakuraNotice);
        return getDataTable(list);
    }

    /**
     * 导出公告列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:notice:export')")
    @Log(title = "C端公告", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraNotice sakuraNotice)
    {
        List<SakuraNotice> list = sakuraNoticeService.selectSakuraNoticeList(sakuraNotice);
        ExcelUtil<SakuraNotice> util = new ExcelUtil<SakuraNotice>(SakuraNotice.class);
        util.exportExcel(response, list, "C端公告数据");
    }

    /**
     * 获取公告详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:notice:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraNoticeService.selectSakuraNoticeById(id));
    }

    /**
     * 新增公告
     */
    @PreAuthorize("@ss.hasPermi('sakura:notice:add')")
    @Log(title = "C端公告", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraNotice sakuraNotice)
    {
        // 新增：先补默认值再校验（默认值仅在新增时填充，避免编辑时静默覆盖已有字段）
        applyInsertDefaults(sakuraNotice);
        validate(sakuraNotice);
        return toAjax(sakuraNoticeService.insertSakuraNotice(sakuraNotice));
    }

    /**
     * 修改公告
     */
    @PreAuthorize("@ss.hasPermi('sakura:notice:edit')")
    @Log(title = "C端公告", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraNotice sakuraNotice)
    {
        if (Objects.isNull(sakuraNotice) || Objects.isNull(sakuraNotice.getId()))
        {
            log.error("修改公告失败：主键为空");
            throw new ServiceException("参数不能为空");
        }
        validate(sakuraNotice);
        return toAjax(sakuraNoticeService.updateSakuraNotice(sakuraNotice));
    }

    /**
     * 删除公告
     */
    @PreAuthorize("@ss.hasPermi('sakura:notice:remove')")
    @Log(title = "C端公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        log.info("删除公告, operator={}, ids={}", getUsername(), Arrays.toString(ids));
        return toAjax(sakuraNoticeService.deleteSakuraNoticeByIds(ids));
    }

    /**
     * 新增时填充默认值（仅作用于新增，避免编辑场景静默覆盖已有字段）：
     * 状态缺省"显示"、排序缺省 0、浏览量缺省 0、是否视频/置顶缺省"否"、
     * 公告类型缺省"系统公告"、发布时间缺省当前时间。
     *
     * @param sakuraNotice 公告
     */
    private void applyInsertDefaults(SakuraNotice sakuraNotice)
    {
        if (Objects.isNull(sakuraNotice))
        {
            return;
        }
        // 状态缺省按"显示"
        if (StrUtil.isBlank(sakuraNotice.getStatus()))
        {
            sakuraNotice.setStatus("0");
        }
        // 排序缺省 0
        if (Objects.isNull(sakuraNotice.getSortOrder()))
        {
            sakuraNotice.setSortOrder(0);
        }
        // 浏览量缺省 0
        if (Objects.isNull(sakuraNotice.getViewCount()))
        {
            sakuraNotice.setViewCount(0L);
        }
        // 是否视频缺省"否"
        if (StrUtil.isBlank(sakuraNotice.getIsVideo()))
        {
            sakuraNotice.setIsVideo(FLAG_NO);
        }
        // 是否置顶缺省"否"
        if (StrUtil.isBlank(sakuraNotice.getIsTop()))
        {
            sakuraNotice.setIsTop(FLAG_NO);
        }
        // 公告类型缺省"系统公告"
        if (StrUtil.isBlank(sakuraNotice.getNoticeType()))
        {
            sakuraNotice.setNoticeType(NOTICE_TYPE_SYSTEM);
        }
        // 发布时间缺省当前时间
        if (Objects.isNull(sakuraNotice.getPublishTime()))
        {
            sakuraNotice.setPublishTime(new Date());
        }
    }

    /**
     * 入参合法性校验（纯校验，不修改入参，避免编辑时把未提交字段改写成默认值）：
     * 标题、内容必填；公告类型/标志位必须在白名单内；视频公告必须带视频地址；时间区间必须先开始后结束。
     *
     * @param sakuraNotice 公告
     */
    private void validate(SakuraNotice sakuraNotice)
    {
        if (Objects.isNull(sakuraNotice))
        {
            throw new ServiceException("参数不能为空");
        }
        // 标题必填
        if (StrUtil.isBlank(sakuraNotice.getTitle()))
        {
            throw new ServiceException("标题不能为空");
        }
        // 内容必填
        if (StrUtil.isBlank(sakuraNotice.getContent()))
        {
            throw new ServiceException("内容不能为空");
        }
        // 公告类型：非空时必须在白名单内
        String noticeType = sakuraNotice.getNoticeType();
        if (StrUtil.isNotBlank(noticeType)
                && !NOTICE_TYPE_SYSTEM.equals(noticeType)
                && !NOTICE_TYPE_ACTIVITY.equals(noticeType)
                && !NOTICE_TYPE_UPDATE.equals(noticeType))
        {
            log.error("公告类型非法: {}", noticeType);
            throw new ServiceException("公告类型不支持");
        }
        // 是否视频：非空时只能是 0/1
        String isVideo = sakuraNotice.getIsVideo();
        if (StrUtil.isNotBlank(isVideo) && !FLAG_NO.equals(isVideo) && !FLAG_YES.equals(isVideo))
        {
            log.error("是否视频标志非法: {}", isVideo);
            throw new ServiceException("视频标志不合法");
        }
        // 是否置顶：非空时只能是 0/1
        String isTop = sakuraNotice.getIsTop();
        if (StrUtil.isNotBlank(isTop) && !FLAG_NO.equals(isTop) && !FLAG_YES.equals(isTop))
        {
            log.error("是否置顶标志非法: {}", isTop);
            throw new ServiceException("置顶标志不合法");
        }
        // 视频公告必须配置视频地址
        if (FLAG_YES.equals(isVideo) && StrUtil.isBlank(sakuraNotice.getVideoUrl()))
        {
            throw new ServiceException("视频地址不能为空");
        }
        // 时间区间校验
        if (Objects.nonNull(sakuraNotice.getStartTime()) && Objects.nonNull(sakuraNotice.getEndTime())
                && sakuraNotice.getStartTime().after(sakuraNotice.getEndTime()))
        {
            throw new ServiceException("结束时间需晚于开始时间");
        }
    }
}
