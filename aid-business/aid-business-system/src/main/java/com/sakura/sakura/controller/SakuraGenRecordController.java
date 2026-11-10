package com.sakura.sakura.controller;

import java.util.List;
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
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.service.ISakuraGenRecordService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * AI生图/生视频抽卡记录Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/genrecord")
public class SakuraGenRecordController extends BaseController
{
    @Autowired
    private ISakuraGenRecordService sakuraGenRecordService;

    /**
     * 查询AI生图/生视频抽卡记录列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:genrecord:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraGenRecord sakuraGenRecord)
    {
        startPage();
        List<SakuraGenRecord> list = sakuraGenRecordService.selectSakuraGenRecordList(sakuraGenRecord);
        return getDataTable(list);
    }

    /**
     * 导出AI生图/生视频抽卡记录列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:genrecord:export')")
    @Log(title = "AI生图/生视频抽卡记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraGenRecord sakuraGenRecord)
    {
        List<SakuraGenRecord> list = sakuraGenRecordService.selectSakuraGenRecordList(sakuraGenRecord);
        ExcelUtil<SakuraGenRecord> util = new ExcelUtil<SakuraGenRecord>(SakuraGenRecord.class);
        util.exportExcel(response, list, "AI生图/生视频抽卡记录数据");
    }

    /**
     * 获取AI生图/生视频抽卡记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:genrecord:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraGenRecordService.selectSakuraGenRecordById(id));
    }

    /**
     * 新增AI生图/生视频抽卡记录
     * 生成记录由系统写入，禁止 UI 层手动新增。
     */
    @PreAuthorize("@ss.hasPermi('sakura:genrecord:add')")
    @Log(title = "AI生图/生视频抽卡记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraGenRecord sakuraGenRecord)
    {
        return AjaxResult.error(403, "该表由系统写入，禁止手动新增");
    }

    /**
     * 修改AI生图/生视频抽卡记录
     * 禁止修改 userId / genType / fileUrl / costCredits / isSelected 等关键字段。
     */
    @PreAuthorize("@ss.hasPermi('sakura:genrecord:edit')")
    @Log(title = "AI生图/生视频抽卡记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraGenRecord sakuraGenRecord)
    {
        return AjaxResult.error(403, "该表由系统写入，禁止手动修改");
    }

    /**
     * 删除AI生图/生视频抽卡记录
     * 禁止删除生成记录。
     */
    @PreAuthorize("@ss.hasPermi('sakura:genrecord:remove')")
    @Log(title = "AI生图/生视频抽卡记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return AjaxResult.error(403, "该表由系统写入，禁止手动删除");
    }
}
