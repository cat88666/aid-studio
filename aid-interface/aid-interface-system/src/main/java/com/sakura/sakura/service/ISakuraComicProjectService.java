package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.vo.SakuraPublicProjectVo;
import com.sakura.sakura.domain.vo.SakuraPublishItemVo;

/**
 * 漫剧项目主Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraComicProjectService extends IService<SakuraComicProject>
{
    /**
     * 查询漫剧项目主
     *
     * @param id 漫剧项目主主键
     * @return 漫剧项目主
     */
    public SakuraComicProject selectSakuraComicProjectById(Long id);

    /**
     * 查询漫剧项目主列表
     *
     * @param sakuraComicProject 漫剧项目主
     * @return 漫剧项目主集合
     */
    public List<SakuraComicProject> selectSakuraComicProjectList(SakuraComicProject sakuraComicProject);

    /**
     * 新增漫剧项目主
     *
     * @param sakuraComicProject 漫剧项目主
     * @return 结果
     */
    public int insertSakuraComicProject(SakuraComicProject sakuraComicProject);

    /**
     * 修改漫剧项目主
     *
     * @param sakuraComicProject 漫剧项目主
     * @return 结果
     */
    public int updateSakuraComicProject(SakuraComicProject sakuraComicProject);

    /**
     * 批量删除漫剧项目主
     *
     * @param ids 需要删除的漫剧项目主主键集合
     * @return 结果
     */
    public int deleteSakuraComicProjectByIds(Long[] ids);

    /**
     * 删除漫剧项目主信息
     *
     * @param id 漫剧项目主主键
     * @return 结果
     */
    public int deleteSakuraComicProjectById(Long id);

    /**
     * 后台发布管理列表（联表作者信息）
     *
     * @param publishState 发布状态筛选（approved=过审未发布 published=已发布，可空）
     * @param projectName  作品名称模糊搜索（可空）
     * @param projectType  作品类型筛选（可空）
     * @param keyword      作者关键字（昵称/邮箱/手机号，可空）
     * @return 发布管理列表
     */
    List<SakuraPublishItemVo> selectPublishItemVoList(String publishState, String projectName,
                                                   String projectType, String keyword);

    /**
     * C端公开广场列表（联表作者昵称，已公开且状态审核中/审核通过，按发布时间倒序）
     *
     * @param projectName 作品名称模糊搜索（可空）
     * @param projectType 作品类型筛选（movie/series，可空）
     * @return 公开广场项目列表
     */
    List<SakuraPublicProjectVo> selectPublicProjectVoList(String projectName, String projectType);
}
