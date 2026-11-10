package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraComicEpisode;

/**
 * 剧集信息Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraComicEpisodeService extends IService<SakuraComicEpisode>
{
    /**
     * 查询剧集信息
     *
     * @param id 剧集信息主键
     * @return 剧集信息
     */
    public SakuraComicEpisode selectSakuraComicEpisodeById(Long id);

    /**
     * 查询剧集信息列表
     *
     * @param sakuraComicEpisode 剧集信息
     * @return 剧集信息集合
     */
    public List<SakuraComicEpisode> selectSakuraComicEpisodeList(SakuraComicEpisode sakuraComicEpisode);

    /**
     * 新增剧集信息
     *
     * @param sakuraComicEpisode 剧集信息
     * @return 结果
     */
    public int insertSakuraComicEpisode(SakuraComicEpisode sakuraComicEpisode);

    /**
     * 修改剧集信息
     *
     * @param sakuraComicEpisode 剧集信息
     * @return 结果
     */
    public int updateSakuraComicEpisode(SakuraComicEpisode sakuraComicEpisode);

    /**
     * 批量删除剧集信息
     *
     * @param ids 需要删除的剧集信息主键集合
     * @return 结果
     */
    public int deleteSakuraComicEpisodeByIds(Long[] ids);

    /**
     * 删除剧集信息信息
     *
     * @param id 剧集信息主键
     * @return 结果
     */
    public int deleteSakuraComicEpisodeById(Long id);
}
