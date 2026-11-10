package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraComicProjectMapper;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.vo.SakuraPublicProjectVo;
import com.sakura.sakura.domain.vo.SakuraPublishItemVo;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.util.HiddenStylePromptJsonUtils;
import com.sakura.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

/**
 * 漫剧项目主Service业务层处理
 *
 * @author 视觉AID
 */
@Service
@Slf4j
public class SakuraComicProjectServiceImpl extends ServiceImpl<SakuraComicProjectMapper, SakuraComicProject> implements ISakuraComicProjectService
{
    @Autowired
    private SakuraComicProjectMapper sakuraComicProjectMapper;

    /**
     * 查询漫剧项目主
     *
     * @param id 漫剧项目主主键
     * @return 漫剧项目主
     */
    @Override
    public SakuraComicProject selectSakuraComicProjectById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询漫剧项目主列表
     *
     * @param sakuraComicProject 漫剧项目主
     * @return 漫剧项目主
     */
    @Override
    public List<SakuraComicProject> selectSakuraComicProjectList(SakuraComicProject sakuraComicProject)
    {
        LambdaQueryWrapper<SakuraComicProject> wrapper = Wrappers.lambdaQuery();
        // 查询字段精简：后台列表不加载隐藏快照，详情接口按主键读取完整记录。
        wrapper.select(SakuraComicProject::getId, SakuraComicProject::getUserId,
                SakuraComicProject::getProjectName, SakuraComicProject::getProjectDesc,
                SakuraComicProject::getPendingProjectName, SakuraComicProject::getPendingProjectDesc,
                SakuraComicProject::getProjectType, SakuraComicProject::getCoverUrl,
                SakuraComicProject::getPendingCoverUrl, SakuraComicProject::getAspectRatio,
                SakuraComicProject::getScriptType, SakuraComicProject::getVideoStyleType,
                SakuraComicProject::getVideoStyleValue, SakuraComicProject::getDefaultGenMode,
                SakuraComicProject::getDefaultCreationMode, SakuraComicProject::getCurrentStep,
                SakuraComicProject::getStatus, SakuraComicProject::getStatusReason,
                SakuraComicProject::getIsPublic, SakuraComicProject::getPublishTime,
                SakuraComicProject::getDelFlag, SakuraComicProject::getCreateTime,
                SakuraComicProject::getCreateBy, SakuraComicProject::getUpdateTime,
                SakuraComicProject::getUpdateBy, SakuraComicProject::getRemark);
        if (sakuraComicProject != null)
        {
            if (sakuraComicProject.getUserId() != null)
            {
                wrapper.eq(SakuraComicProject::getUserId, sakuraComicProject.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getProjectName()))
            {
                wrapper.like(SakuraComicProject::getProjectName, sakuraComicProject.getProjectName());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getProjectType()))
            {
                wrapper.eq(SakuraComicProject::getProjectType, sakuraComicProject.getProjectType());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getAspectRatio()))
            {
                wrapper.eq(SakuraComicProject::getAspectRatio, sakuraComicProject.getAspectRatio());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getScriptType()))
            {
                wrapper.eq(SakuraComicProject::getScriptType, sakuraComicProject.getScriptType());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getVideoStyleType()))
            {
                wrapper.eq(SakuraComicProject::getVideoStyleType, sakuraComicProject.getVideoStyleType());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getDefaultGenMode()))
            {
                wrapper.eq(SakuraComicProject::getDefaultGenMode, sakuraComicProject.getDefaultGenMode());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getDefaultCreationMode()))
            {
                wrapper.eq(SakuraComicProject::getDefaultCreationMode, sakuraComicProject.getDefaultCreationMode());
            }
            if (sakuraComicProject.getCurrentStep() != null)
            {
                wrapper.eq(SakuraComicProject::getCurrentStep, sakuraComicProject.getCurrentStep());
            }
            if (sakuraComicProject.getStatus() != null)
            {
                wrapper.eq(SakuraComicProject::getStatus, sakuraComicProject.getStatus());
            }
            if (StrUtil.isNotBlank(sakuraComicProject.getIsPublic()))
            {
                wrapper.eq(SakuraComicProject::getIsPublic, sakuraComicProject.getIsPublic());
            }
        }
        wrapper.orderByDesc(SakuraComicProject::getId);
        return this.list(wrapper);
    }

    /**
     * 新增漫剧项目主
     *
     * @param sakuraComicProject 漫剧项目主
     * @return 结果
     */
    @Override
    public int insertSakuraComicProject(SakuraComicProject sakuraComicProject)
    {
        sakuraComicProject.setHiddenStylePromptJson(normalizeHiddenStylePrompt(
                sakuraComicProject.getHiddenStylePromptJson(), sakuraComicProject.getId()));
        sakuraComicProject.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraComicProject) ? 1 : 0;
    }

    /**
     * 修改漫剧项目主
     *
     * @param sakuraComicProject 漫剧项目主
     * @return 结果
     */
    @Override
    public int updateSakuraComicProject(SakuraComicProject sakuraComicProject)
    {
        // 项目公开风格与隐藏模板共同组成不可漂移快照，后台普通编辑不可改写其中任一部分。
        sakuraComicProject.setVideoStyleType(null);
        sakuraComicProject.setVideoStyleValue(null);
        sakuraComicProject.setHiddenStylePromptJson(null);
        sakuraComicProject.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraComicProject) ? 1 : 0;
    }

    /**
     * 批量删除漫剧项目主
     *
     * @param ids 需要删除的漫剧项目主主键
     * @return 结果
     */
    @Override
    public int deleteSakuraComicProjectByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除漫剧项目主信息
     *
     * @param id 漫剧项目主主键
     * @return 结果
     */
    @Override
    public int deleteSakuraComicProjectById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }

    private String normalizeHiddenStylePrompt(String json, Long id)
    {
        try
        {
            return HiddenStylePromptJsonUtils.normalize(json);
        }
        catch (IllegalArgumentException e)
        {
            log.error("项目隐藏风格快照格式错误: id={}", id, e);
            throw new ServiceException("隐藏风格格式错误");
        }
    }

    /**
     * 后台发布管理列表（联表作者信息，SQL 见 SakuraComicProjectMapper.xml）
     *
     * @param publishState 发布状态筛选（approved=过审未发布 published=已发布，可空）
     * @param projectName  作品名称模糊搜索（可空）
     * @param projectType  作品类型筛选（可空）
     * @param keyword      作者关键字（昵称/邮箱/手机号，可空）
     * @return 发布管理列表
     */
    @Override
    public List<SakuraPublishItemVo> selectPublishItemVoList(String publishState, String projectName,
                                                          String projectType, String keyword)
    {
        return sakuraComicProjectMapper.selectPublishItemVoList(publishState, projectName, projectType, keyword);
    }

    /**
     * C端公开广场列表（联表作者昵称，SQL 见 SakuraComicProjectMapper.xml）
     *
     * @param projectName 作品名称模糊搜索（可空）
     * @param projectType 作品类型筛选（movie/series，可空）
     * @return 公开广场项目列表
     */
    @Override
    public List<SakuraPublicProjectVo> selectPublicProjectVoList(String projectName, String projectType)
    {
        return sakuraComicProjectMapper.selectPublicProjectVoList(projectName, projectType);
    }
}
