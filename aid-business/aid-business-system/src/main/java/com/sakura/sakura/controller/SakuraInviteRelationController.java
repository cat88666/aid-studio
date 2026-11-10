package com.sakura.sakura.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakura.sakura.domain.SakuraInviteRelation;
import com.sakura.sakura.domain.vo.SakuraInviteRelationVo;
import com.sakura.sakura.service.ISakuraInviteRelationService;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.core.page.TableDataInfo;
import com.sakura.common.enums.BusinessType;

/**
 * 邀请关系Controller（后台管理）
 * 邀请关系由注册链路自动建立，后台只允许查询与风控处置（禁用/恢复），禁止手动增删。
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/sakura/inviterelation")
public class SakuraInviteRelationController extends BaseController
{
    @Autowired
    private ISakuraInviteRelationService sakuraInviteRelationService;

    /**
     * 查询邀请关系列表（联表返回邀请人/被邀请人昵称账号）
     */
    @PreAuthorize("@ss.hasPermi('sakura:inviterelation:list')")
    @GetMapping("/list")
    public TableDataInfo list(SakuraInviteRelationVo query)
    {
        startPage();
        List<SakuraInviteRelationVo> list = sakuraInviteRelationService.selectInviteRelationVoList(query);
        return getDataTable(list);
    }

    /**
     * 获取邀请关系详细信息
     */
    @PreAuthorize("@ss.hasPermi('sakura:inviterelation:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraInviteRelationService.getById(id));
    }

    /**
     * 禁用/恢复邀请关系（风控处置：禁用后该关系不再产生返佣，历史返佣不受影响）
     * 入参仅取 id / status（0正常 1禁用） / remark（处置备注），其余字段忽略。
     */
    @PreAuthorize("@ss.hasPermi('sakura:inviterelation:edit')")
    @Log(title = "邀请关系", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SakuraInviteRelation relation)
    {
        sakuraInviteRelationService.changeStatus(relation.getId(), relation.getStatus(),
                relation.getRemark(), getUsername());
        return AjaxResult.success("操作成功");
    }
}
