package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraEpisodeEditorMapper;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.service.ISakuraEpisodeEditorService;

/**
 * 剧集视频剪辑与成片最新状态Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraEpisodeEditorServiceImpl extends ServiceImpl<SakuraEpisodeEditorMapper, SakuraEpisodeEditor> implements ISakuraEpisodeEditorService
{
    @Autowired
    private SakuraEpisodeEditorMapper sakuraEpisodeEditorMapper;

    /**
     * 查询剧集视频剪辑与成片最新状态
     *
     * @param id 剧集视频剪辑与成片最新状态主键
     * @return 剧集视频剪辑与成片最新状态
     */
    @Override
    public SakuraEpisodeEditor selectSakuraEpisodeEditorById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询剧集视频剪辑与成片最新状态列表
     *
     * @param sakuraEpisodeEditor 剧集视频剪辑与成片最新状态
     * @return 剧集视频剪辑与成片最新状态
     */
    @Override
    public List<SakuraEpisodeEditor> selectSakuraEpisodeEditorList(SakuraEpisodeEditor sakuraEpisodeEditor)
    {
        LambdaQueryWrapper<SakuraEpisodeEditor> wrapper = Wrappers.lambdaQuery();
        if (sakuraEpisodeEditor != null)
        {
            if (sakuraEpisodeEditor.getProjectId() != null)
            {
                wrapper.eq(SakuraEpisodeEditor::getProjectId, sakuraEpisodeEditor.getProjectId());
            }
            if (sakuraEpisodeEditor.getEpisodeId() != null)
            {
                wrapper.eq(SakuraEpisodeEditor::getEpisodeId, sakuraEpisodeEditor.getEpisodeId());
            }
            if (sakuraEpisodeEditor.getUserId() != null)
            {
                wrapper.eq(SakuraEpisodeEditor::getUserId, sakuraEpisodeEditor.getUserId());
            }
            if (sakuraEpisodeEditor.getExportStatus() != null)
            {
                wrapper.eq(SakuraEpisodeEditor::getExportStatus, sakuraEpisodeEditor.getExportStatus());
            }
        }
        wrapper.orderByDesc(SakuraEpisodeEditor::getId);
        return this.list(wrapper);
    }

    /**
     * 新增剧集视频剪辑与成片最新状态
     *
     * @param sakuraEpisodeEditor 剧集视频剪辑与成片最新状态
     * @return 结果
     */
    @Override
    public int insertSakuraEpisodeEditor(SakuraEpisodeEditor sakuraEpisodeEditor)
    {
        sakuraEpisodeEditor.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraEpisodeEditor) ? 1 : 0;
    }

    /**
     * 修改剧集视频剪辑与成片最新状态
     *
     * @param sakuraEpisodeEditor 剧集视频剪辑与成片最新状态
     * @return 结果
     */
    @Override
    public int updateSakuraEpisodeEditor(SakuraEpisodeEditor sakuraEpisodeEditor)
    {
        sakuraEpisodeEditor.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraEpisodeEditor) ? 1 : 0;
    }

    /**
     * 批量删除剧集视频剪辑与成片最新状态
     *
     * @param ids 需要删除的剧集视频剪辑与成片最新状态主键
     * @return 结果
     */
    @Override
    public int deleteSakuraEpisodeEditorByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除剧集视频剪辑与成片最新状态信息
     *
     * @param id 剧集视频剪辑与成片最新状态主键
     * @return 结果
     */
    @Override
    public int deleteSakuraEpisodeEditorById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
