package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraEpisodeEditor;

/**
 * 剧集视频剪辑与成片最新状态Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraEpisodeEditorService extends IService<SakuraEpisodeEditor>
{
    /**
     * 查询剧集视频剪辑与成片最新状态
     *
     * @param id 剧集视频剪辑与成片最新状态主键
     * @return 剧集视频剪辑与成片最新状态
     */
    public SakuraEpisodeEditor selectSakuraEpisodeEditorById(Long id);

    /**
     * 查询剧集视频剪辑与成片最新状态列表
     *
     * @param sakuraEpisodeEditor 剧集视频剪辑与成片最新状态
     * @return 剧集视频剪辑与成片最新状态集合
     */
    public List<SakuraEpisodeEditor> selectSakuraEpisodeEditorList(SakuraEpisodeEditor sakuraEpisodeEditor);

    /**
     * 新增剧集视频剪辑与成片最新状态
     *
     * @param sakuraEpisodeEditor 剧集视频剪辑与成片最新状态
     * @return 结果
     */
    public int insertSakuraEpisodeEditor(SakuraEpisodeEditor sakuraEpisodeEditor);

    /**
     * 修改剧集视频剪辑与成片最新状态
     *
     * @param sakuraEpisodeEditor 剧集视频剪辑与成片最新状态
     * @return 结果
     */
    public int updateSakuraEpisodeEditor(SakuraEpisodeEditor sakuraEpisodeEditor);

    /**
     * 批量删除剧集视频剪辑与成片最新状态
     *
     * @param ids 需要删除的剧集视频剪辑与成片最新状态主键集合
     * @return 结果
     */
    public int deleteSakuraEpisodeEditorByIds(Long[] ids);

    /**
     * 删除剧集视频剪辑与成片最新状态信息
     *
     * @param id 剧集视频剪辑与成片最新状态主键
     * @return 结果
     */
    public int deleteSakuraEpisodeEditorById(Long id);
}
