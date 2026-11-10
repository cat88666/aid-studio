package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraRolePropSceneFormMapper;
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.domain.SakuraRolePropSceneForm;
import com.sakura.sakura.service.ISakuraRolePropSceneService;
import com.sakura.sakura.service.ISakuraRolePropSceneFormService;
import lombok.extern.slf4j.Slf4j;

/**
 * 角色道具场景形态(从)Service业务层处理
 *
 * @author 视觉AID
 */
@Service
@Slf4j
public class SakuraRolePropSceneFormServiceImpl extends ServiceImpl<SakuraRolePropSceneFormMapper, SakuraRolePropSceneForm> implements ISakuraRolePropSceneFormService
{
    private static final String DEL_FLAG_NORMAL = "0";
    private static final String DELETE_REASON_AUTO_OVERWRITE = "auto_overwrite";

    @Autowired
    private ISakuraRolePropSceneService assetService;

    /**
     * 查询角色道具场景形态(从)
     *
     * @param id 角色道具场景形态(从)主键
     * @return 角色道具场景形态(从)
     */
    @Override
    public SakuraRolePropSceneForm selectSakuraRolePropSceneFormById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询角色道具场景形态(从)列表
     *
     * @param sakuraRolePropSceneForm 角色道具场景形态(从)
     * @return 角色道具场景形态(从)
     */
    @Override
    public List<SakuraRolePropSceneForm> selectSakuraRolePropSceneFormList(SakuraRolePropSceneForm sakuraRolePropSceneForm)
    {
        LambdaQueryWrapper<SakuraRolePropSceneForm> wrapper = Wrappers.lambdaQuery();
        if (sakuraRolePropSceneForm != null)
        {
            if (sakuraRolePropSceneForm.getAssetId() != null)
            {
                wrapper.eq(SakuraRolePropSceneForm::getAssetId, sakuraRolePropSceneForm.getAssetId());
            }
            if (sakuraRolePropSceneForm.getProjectId() != null)
            {
                wrapper.eq(SakuraRolePropSceneForm::getProjectId, sakuraRolePropSceneForm.getProjectId());
            }
            if (sakuraRolePropSceneForm.getEpisodeId() != null)
            {
                wrapper.eq(SakuraRolePropSceneForm::getEpisodeId, sakuraRolePropSceneForm.getEpisodeId());
            }
            if (sakuraRolePropSceneForm.getUserId() != null)
            {
                wrapper.eq(SakuraRolePropSceneForm::getUserId, sakuraRolePropSceneForm.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraRolePropSceneForm.getName()))
            {
                wrapper.like(SakuraRolePropSceneForm::getName, sakuraRolePropSceneForm.getName());
            }
            if (StrUtil.isNotBlank(sakuraRolePropSceneForm.getVisualDescStatus()))
            {
                wrapper.eq(SakuraRolePropSceneForm::getVisualDescStatus, sakuraRolePropSceneForm.getVisualDescStatus());
            }
            if (StrUtil.isNotBlank(sakuraRolePropSceneForm.getCreateSource()))
            {
                wrapper.eq(SakuraRolePropSceneForm::getCreateSource, sakuraRolePropSceneForm.getCreateSource());
            }
        }
        wrapper.orderByDesc(SakuraRolePropSceneForm::getId);
        return this.list(wrapper);
    }

    /**
     * 新增角色道具场景形态(从)
     *
     * @param sakuraRolePropSceneForm 角色道具场景形态(从)
     * @return 结果
     */
    @Override
    public int insertSakuraRolePropSceneForm(SakuraRolePropSceneForm sakuraRolePropSceneForm)
    {
        requireEditableRoot(sakuraRolePropSceneForm == null ? null : sakuraRolePropSceneForm.getAssetId());
        sakuraRolePropSceneForm.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraRolePropSceneForm) ? 1 : 0;
    }

    /**
     * 修改角色道具场景形态(从)
     *
     * @param sakuraRolePropSceneForm 角色道具场景形态(从)
     * @return 结果
     */
    @Override
    public int updateSakuraRolePropSceneForm(SakuraRolePropSceneForm sakuraRolePropSceneForm)
    {
        if (sakuraRolePropSceneForm == null || sakuraRolePropSceneForm.getId() == null)
        {
            log.info("后台更新资产形态失败，形态ID为空");
            throw new ServiceException("形态不能为空");
        }
        SakuraRolePropSceneForm stored = this.getById(sakuraRolePropSceneForm.getId());
        if (stored == null)
        {
            return 0;
        }
        requireEditableRoot(stored.getAssetId());
        validateImmutableIdentity(stored, sakuraRolePropSceneForm);
        sakuraRolePropSceneForm.setUpdateTime(DateUtils.getNowDate());
        return this.update(sakuraRolePropSceneForm, Wrappers.<SakuraRolePropSceneForm>lambdaUpdate()
                .eq(SakuraRolePropSceneForm::getId, stored.getId())
                .eq(SakuraRolePropSceneForm::getDelFlag, DEL_FLAG_NORMAL)) ? 1 : 0;
    }

    private SakuraRolePropScene requireEditableRoot(Long assetId)
    {
        SakuraRolePropScene root = assetId == null ? null : assetService.getById(assetId);
        if (root == null || !Objects.equals(DEL_FLAG_NORMAL, root.getDelFlag()))
        {
            if (root != null && Objects.equals(DELETE_REASON_AUTO_OVERWRITE, root.getDeleteReason()))
            {
                log.info("后台更新自动覆盖资产形态被拒绝: assetId={}", assetId);
                throw new ServiceException("资产不可恢复");
            }
            log.info("后台更新资产形态失败，主资产无效: assetId={}", assetId);
            throw new ServiceException("资产已删除");
        }
        return root;
    }

    private void validateImmutableIdentity(SakuraRolePropSceneForm stored, SakuraRolePropSceneForm update)
    {
        boolean changed = update.getAssetId() != null
                && !Objects.equals(stored.getAssetId(), update.getAssetId());
        changed = changed || update.getProjectId() != null
                && !Objects.equals(stored.getProjectId(), update.getProjectId());
        changed = changed || update.getEpisodeId() != null
                && !Objects.equals(stored.getEpisodeId(), update.getEpisodeId());
        changed = changed || update.getUserId() != null
                && !Objects.equals(stored.getUserId(), update.getUserId());
        changed = changed || update.getCreateSource() != null
                && !Objects.equals(stored.getCreateSource(), update.getCreateSource());
        changed = changed || update.getDelFlag() != null
                && !Objects.equals(stored.getDelFlag(), update.getDelFlag());
        if (changed)
        {
            log.info("后台更新资产形态不可变身份被拒绝: formId={}", stored.getId());
            throw new ServiceException("形态归属不可改");
        }
        if (!Objects.equals(DEL_FLAG_NORMAL, stored.getDelFlag()))
        {
            log.info("后台更新已删除资产形态被拒绝: formId={}", stored.getId());
            throw new ServiceException("形态已删除");
        }
    }

    /**
     * 批量删除角色道具场景形态(从)
     *
     * @param ids 需要删除的角色道具场景形态(从)主键
     * @return 结果
     */
    @Override
    public int deleteSakuraRolePropSceneFormByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除角色道具场景形态(从)信息
     *
     * @param id 角色道具场景形态(从)主键
     * @return 结果
     */
    @Override
    public int deleteSakuraRolePropSceneFormById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
