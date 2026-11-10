package com.sakura.rps.helper;

import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraScenePlot;
import com.sakura.sakura.service.ISakuraScenePlotService;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 场景 sceneCode 全局分配器，让 sceneCode 在 (project_id, episode_id) 维度全局单调递增。
 *
 * @author 视觉AID
 */
@Slf4j
public class SceneCodeAllocator
{
    private final Long projectId;
    private final Long episodeId;
    private int counter;

    /**
     * 构造分配器并初始化计数器为 (DB 当前最大 sceneCode + 1)。
     *
     * @param projectId       项目 ID
     * @param episodeId       剧集 ID
     * @param scenePlotService 剧情节拍 Service，用于查询已存量
     */
    public SceneCodeAllocator(Long projectId, Long episodeId, ISakuraScenePlotService scenePlotService)
    {
        this.projectId = projectId;
        this.episodeId = episodeId;
        this.counter = computeMaxExistingSceneCode(scenePlotService);
        log.info("SceneCodeAllocator 初始化: projectId={}, episodeId={}, 起始计数={}",
                projectId, episodeId, counter);
    }

    /**
     * 返回下一个 sceneCode 字符串（三位补零，如 "001" / "013" / "157"）。
     */
    public String next()
    {
        counter++;
        return String.format("%03d", counter);
    }

    /** 查询 DB 当前最大 sceneCode（仅扫 sakura_scene_plot.scene_code） */
    private int computeMaxExistingSceneCode(ISakuraScenePlotService scenePlotService)
    {
        // 防漏字段：select 仅取 scene_code 与 id（id 用于异常日志定位）；后续新增过滤条件请同步更新 select 列
        LambdaQueryWrapper<SakuraScenePlot> q = Wrappers.lambdaQuery();
        q.select(SakuraScenePlot::getId, SakuraScenePlot::getSceneCode);
        q.eq(SakuraScenePlot::getProjectId, projectId);
        if (Objects.nonNull(episodeId))
        {
            q.eq(SakuraScenePlot::getEpisodeId, episodeId);
        }
        q.eq(SakuraScenePlot::getDelFlag, "0");
        List<SakuraScenePlot> existing = scenePlotService.list(q);

        int max = 0;
        for (SakuraScenePlot p : existing)
        {
            int v = parseCode(p.getSceneCode());
            if (v > max) { max = v; }
        }
        return max;
    }

    /**
     * 把 sceneCode 字符串解析成整数；非数字（含空字符串）返回 0。
     */
    private static int parseCode(String code)
    {
        if (StrUtil.isBlank(code)) { return 0; }
        try
        {
            return Integer.parseInt(code.trim());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }
}
