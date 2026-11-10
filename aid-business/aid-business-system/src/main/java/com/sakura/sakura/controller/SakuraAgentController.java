package com.sakura.sakura.controller;

import java.util.List;

import jakarta.validation.Valid;

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

import com.sakura.agent.ISakuraAgentService;
import com.sakura.agent.dto.AgentQueryRequest;
import com.sakura.sakura.domain.SakuraAgent;
import com.sakura.common.annotation.Log;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.core.page.TableDataInfo;
import com.sakura.common.enums.BusinessType;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.orchestration.IAiOrchestrationService;

import lombok.extern.slf4j.Slf4j;

/**
 * 智能体配置 后台管理 Controller
 * 后台 REST 接口：分页查询、详情、新增、修改、删除。
 * 管理端可见 prompt_content 字段；C 端禁止暴露，参见 {@link com.sakura.sakura.controller.AgentClientController}.
 *
 * @author 视觉AID
 */
@Slf4j
@RestController
@RequestMapping("/sakura/agent")
public class SakuraAgentController extends BaseController
{
    @Autowired
    private ISakuraAgentService sakuraAgentService;

    @Autowired
    private IAiOrchestrationService orchestrationService;

    /**
     * 查询智能体列表（分页 + 条件，支持 bizType 过滤）
     */
    @PreAuthorize("@ss.hasPermi('sakura:agent:list')")
    @GetMapping("/list")
    public TableDataInfo list(AgentQueryRequest query)
    {
        startPage();
        List<SakuraAgent> list = sakuraAgentService.listAgents(query);
        return getDataTable(list);
    }

    /**
     * 按 bizCategoryCode 查询启用的智能体（不分页，供后台下拉使用）。
     * 走 GET，返回含 {@code promptContent} 的 {@link SakuraAgent}，不经过 C 端加密链路；
     * 未启用 / 已删除的智能体自动剔除。
     *
     * @param bizCategoryCode 业务分类编码（= sceneCode）
     * @return 该场景下启用的智能体列表
     */
    @PreAuthorize("@ss.hasPermi('sakura:agent:list')")
    @GetMapping("/listByBizCategory")
    public AjaxResult listByBizCategory(String bizCategoryCode)
    {
        AgentQueryRequest q = new AgentQueryRequest();
        q.setBizCategoryCode(bizCategoryCode);
        q.setStatus(1); // 仅启用（1=启用 0=停用）
        List<SakuraAgent> list = sakuraAgentService.listAgents(q);
        return success(list);
    }

    /**
     * 获取智能体详细信息（管理端可见 promptContent）
     */
    @PreAuthorize("@ss.hasPermi('sakura:agent:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sakuraAgentService.getById(id));
    }

    /**
     * 新增智能体
     */
    @PreAuthorize("@ss.hasPermi('sakura:agent:add')")
    @Log(title = "智能体配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody SakuraAgent agent)
    {
        try
        {
            return toAjax(sakuraAgentService.insertAgent(agent));
        }
        catch (RuntimeException e)
        {
            log.error("新增智能体失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 修改智能体
     */
    @PreAuthorize("@ss.hasPermi('sakura:agent:edit')")
    @Log(title = "智能体配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody SakuraAgent agent)
    {
        try
        {
            return toAjax(sakuraAgentService.updateAgent(agent));
        }
        catch (RuntimeException e)
        {
            log.error("修改智能体失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 删除智能体
     */
    @PreAuthorize("@ss.hasPermi('sakura:agent:remove')")
    @Log(title = "智能体配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id)
    {
        try
        {
            return toAjax(orchestrationService.deleteAgentIfUnreferenced(id, SecurityUtils.getUsername()));
        }
        catch (RuntimeException e)
        {
            log.error("删除智能体失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }
}
