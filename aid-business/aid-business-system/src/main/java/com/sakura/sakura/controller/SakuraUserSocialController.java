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
import com.sakura.sakura.domain.SakuraUserSocial;
import com.sakura.sakura.service.ISakuraUserSocialService;
import com.sakura.common.utils.poi.ExcelUtil;
import com.sakura.common.core.page.TableDataInfo;

/**
 * 用户第三方登录授权Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/thridsocial")
public class SakuraUserSocialController extends BaseController
{
    @Autowired
    private ISakuraUserSocialService sakuraUserSocialService;

    /**
     * 查询用户第三方登录授权列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:thridsocial:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraUserSocial sakuraUserSocial)
    {
        startPage();
        List<SakuraUserSocial> list = sakuraUserSocialService.selectSakuraUserSocialList(sakuraUserSocial);
        return getDataTable(list);
    }

    /**
     * 导出用户第三方登录授权列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:thridsocial:export')")
    @Log(title = "用户第三方登录授权", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SakuraUserSocial sakuraUserSocial)
    {
        List<SakuraUserSocial> list = sakuraUserSocialService.selectSakuraUserSocialList(sakuraUserSocial);
        ExcelUtil<SakuraUserSocial> util = new ExcelUtil<SakuraUserSocial>(SakuraUserSocial.class);
        util.exportExcel(response, list, "用户第三方登录授权数据");
    }

    /**
     * 获取用户第三方登录授权详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:thridsocial:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraUserSocialService.selectSakuraUserSocialById(id));
    }

    /**
     * 新增用户第三方登录授权
     * 第三方登录绑定表禁止手动新增，否则可预注入 openId 让特定用户扫码后被绑定到攻击者账号。
     */
    @PreAuthorize("@ss.hasPermi('sakura:thridsocial:add')")
    @Log(title = "用户第三方登录授权", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SakuraUserSocial sakuraUserSocial)
    {
        return AjaxResult.error(403, "为保障账号安全，第三方绑定关系禁止手动新增");
    }

    /**
     * 修改用户第三方登录授权
     * 禁止篡改 openId/userId 映射关系。
     */
    @PreAuthorize("@ss.hasPermi('sakura:thridsocial:edit')")
    @Log(title = "用户第三方登录授权", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SakuraUserSocial sakuraUserSocial)
    {
        return AjaxResult.error(403, "为保障账号安全，第三方绑定关系禁止手动修改");
    }

    /**
     * 删除用户第三方登录授权
     * 禁止抹掉他人绑定触发登录失败。
     */
    @PreAuthorize("@ss.hasPermi('sakura:thridsocial:remove')")
    @Log(title = "用户第三方登录授权", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return AjaxResult.error(403, "为保障账号安全，第三方绑定关系禁止手动删除");
    }
}
