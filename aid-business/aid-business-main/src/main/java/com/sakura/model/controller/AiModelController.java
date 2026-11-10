package com.sakura.model.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.model.dto.AiModelByFuncRequest;
import com.sakura.model.dto.AiModelListRequest;
import com.sakura.model.service.IAiModelBusinessService;
import com.sakura.model.util.AiModelClientDisplaySorter;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * C端AI模型Controller
 *
 * @author 视觉AID
 */
@RestController
@RequestMapping("/api/user/model")
public class AiModelController extends BaseController
{
    @Resource
    private IAiModelBusinessService aiModelBusinessService;

    /**
     * 查询可用AI模型列表。
     */
    @PostMapping("/list")
    public AjaxResult list(@RequestBody AiModelListRequest request)
    {
        return success(AiModelClientDisplaySorter.sortedModels(
                aiModelBusinessService.listAvailableModels(request)));
    }

    /**
     * 按功能编码批量查询可用AI模型列表（按功能分组返回）。
     * 可选传入 projectId/episodeId：专业版多参出片自动切到 main_storyboard_video_multi_pro。
     */
    @PostMapping("/listByFunc")
    public AjaxResult listByFunc(@Valid @RequestBody AiModelByFuncRequest request)
    {
        return success(AiModelClientDisplaySorter.sortedGroups(
                aiModelBusinessService.listAvailableModelsGroupedByFuncCodes(
                        request.resolveFuncCodes(),
                        request.getProjectId(),
                        request.getEpisodeId(),
                        SecurityUtils.getUserId())));
    }
}
