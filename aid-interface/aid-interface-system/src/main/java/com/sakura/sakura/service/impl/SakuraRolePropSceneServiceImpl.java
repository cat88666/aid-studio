package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.AssetNameNormalizer;
import com.sakura.common.utils.DateUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraRolePropSceneMapper;
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.service.ISakuraRolePropSceneService;
import com.sakura.sakura.service.IStoryboardSceneSnapshotService;
import lombok.extern.slf4j.Slf4j;

/**
 * 角色道具场景Service业务层处理
 *
 * @author 视觉AID
 */
@Service
@Slf4j
public class SakuraRolePropSceneServiceImpl extends ServiceImpl<SakuraRolePropSceneMapper, SakuraRolePropScene> implements ISakuraRolePropSceneService
{
    private static final String DEL_FLAG_NORMAL = "0";
    private static final String DEL_FLAG_DELETED = "2";
    private static final String DELETE_REASON_AUTO_OVERWRITE = "auto_overwrite";
    private static final String ASSET_TYPE_SCENE = "scene";

    @Autowired
    private IStoryboardSceneSnapshotService storyboardSceneSnapshotService;

    @Override
    public String normalizeAssetName(String name)
    {
        return AssetNameNormalizer.normalize(name);
    }

    @Override
    public void validateActiveNameAvailable(Long projectId, Long userId, String assetType,
                                            String name, Long excludeId)
    {
        String normalizedName = validateNameIdentity(projectId, userId, assetType, name);
        LambdaQueryWrapper<SakuraRolePropScene> wrapper = Wrappers.lambdaQuery();
        // 展示名统一经 Java 规范化后按业务身份校验；数据库有效行唯一键兜住并发写入竞态。
        wrapper.select(SakuraRolePropScene::getId, SakuraRolePropScene::getName);
        wrapper.eq(SakuraRolePropScene::getProjectId, projectId);
        wrapper.eq(SakuraRolePropScene::getUserId, userId);
        wrapper.eq(SakuraRolePropScene::getAssetType, assetType);
        wrapper.eq(SakuraRolePropScene::getDelFlag, "0");
        if (excludeId != null)
        {
            wrapper.ne(SakuraRolePropScene::getId, excludeId);
        }
        boolean duplicate = this.list(wrapper).stream()
                .anyMatch(asset -> Objects.equals(normalizedName, normalizeAssetName(asset.getName())));
        if (duplicate)
        {
            log.info("有效资产名称已存在: projectId={}, userId={}, assetType={}, excludeId={}, normalizedName={}",
                    projectId, userId, assetType, excludeId, normalizedName);
            throw new ServiceException("名称已存在");
        }
    }

    @Override
    public boolean save(SakuraRolePropScene entity)
    {
        if (entity == null)
        {
            return false;
        }
        String normalizedName = validateNameIdentity(entity.getProjectId(), entity.getUserId(),
                entity.getAssetType(), entity.getName());
        validateActiveNameAvailable(entity.getProjectId(), entity.getUserId(), entity.getAssetType(),
                entity.getName(), null);
        entity.setNameNormalized(normalizedName);
        try
        {
            return super.save(entity);
        }
        catch (DuplicateKeyException e)
        {
            log.info("新增有效资产发生名称并发冲突: projectId={}, userId={}, assetType={}, normalizedName={}",
                    entity.getProjectId(), entity.getUserId(), entity.getAssetType(), normalizedName);
            throw new ServiceException("名称已存在");
        }
    }

    private String validateNameIdentity(Long projectId, Long userId, String assetType, String name)
    {
        if (projectId == null)
        {
            log.info("有效资产名称校验失败，项目为空: userId={}, assetType={}", userId, assetType);
            throw new ServiceException("项目不能为空");
        }
        if (userId == null)
        {
            log.info("有效资产名称校验失败，用户为空: projectId={}, assetType={}", projectId, assetType);
            throw new ServiceException("用户不能为空");
        }
        if (StrUtil.isBlank(assetType))
        {
            log.info("有效资产名称校验失败，类型为空: projectId={}, userId={}", projectId, userId);
            throw new ServiceException("类型不能为空");
        }
        if (name != null && name.codePointCount(0, name.length()) > AssetNameNormalizer.MAX_DISPLAY_LENGTH)
        {
            log.info("有效资产展示名称过长: projectId={}, userId={}, assetType={}",
                    projectId, userId, assetType);
            throw new ServiceException("名称过长");
        }
        String normalizedName = normalizeAssetName(name);
        if (StrUtil.isBlank(normalizedName))
        {
            log.info("有效资产名称校验失败，名称为空: projectId={}, userId={}, assetType={}",
                    projectId, userId, assetType);
            throw new ServiceException("名称不能为空");
        }
        if (normalizedName.codePointCount(0, normalizedName.length()) > AssetNameNormalizer.MAX_NORMALIZED_LENGTH)
        {
            log.info("有效资产名称校验失败，规范化名称过长: projectId={}, userId={}, assetType={}",
                    projectId, userId, assetType);
            throw new ServiceException("名称过长");
        }
        return normalizedName;
    }

    /**
     * 查询角色道具场景
     *
     * @param id 角色道具场景主键
     * @return 角色道具场景
     */
    @Override
    public SakuraRolePropScene selectSakuraRolePropSceneById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询角色道具场景列表
     *
     * @param sakuraRolePropScene 角色道具场景
     * @return 角色道具场景
     */
    @Override
    public List<SakuraRolePropScene> selectSakuraRolePropSceneList(SakuraRolePropScene sakuraRolePropScene)
    {
        LambdaQueryWrapper<SakuraRolePropScene> wrapper = Wrappers.lambdaQuery();
        if (sakuraRolePropScene != null)
        {
            if (sakuraRolePropScene.getProjectId() != null)
            {
                wrapper.eq(SakuraRolePropScene::getProjectId, sakuraRolePropScene.getProjectId());
            }
            if (sakuraRolePropScene.getEpisodeId() != null)
            {
                wrapper.eq(SakuraRolePropScene::getEpisodeId, sakuraRolePropScene.getEpisodeId());
            }
            if (sakuraRolePropScene.getUserId() != null)
            {
                wrapper.eq(SakuraRolePropScene::getUserId, sakuraRolePropScene.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraRolePropScene.getName()))
            {
                wrapper.like(SakuraRolePropScene::getName, sakuraRolePropScene.getName());
            }
            if (StrUtil.isNotBlank(sakuraRolePropScene.getAssetType()))
            {
                wrapper.eq(SakuraRolePropScene::getAssetType, sakuraRolePropScene.getAssetType());
            }
            if (StrUtil.isNotBlank(sakuraRolePropScene.getGender()))
            {
                wrapper.eq(SakuraRolePropScene::getGender, sakuraRolePropScene.getGender());
            }
            if (StrUtil.isNotBlank(sakuraRolePropScene.getAgeRange()))
            {
                wrapper.eq(SakuraRolePropScene::getAgeRange, sakuraRolePropScene.getAgeRange());
            }
            if (StrUtil.isNotBlank(sakuraRolePropScene.getRoleLevel()))
            {
                wrapper.eq(SakuraRolePropScene::getRoleLevel, sakuraRolePropScene.getRoleLevel());
            }
            if (StrUtil.isNotBlank(sakuraRolePropScene.getCreateSource()))
            {
                wrapper.eq(SakuraRolePropScene::getCreateSource, sakuraRolePropScene.getCreateSource());
            }
            if (sakuraRolePropScene.getHasCrowd() != null)
            {
                wrapper.eq(SakuraRolePropScene::getHasCrowd, sakuraRolePropScene.getHasCrowd());
            }
        }
        wrapper.orderByAsc(SakuraRolePropScene::getFirstSceneCode).orderByDesc(SakuraRolePropScene::getId);
        return this.list(wrapper);
    }

    /**
     * 新增角色道具场景
     *
     * @param sakuraRolePropScene 角色道具场景
     * @return 结果
     */
    @Override
    public int insertSakuraRolePropScene(SakuraRolePropScene sakuraRolePropScene)
    {
        sakuraRolePropScene.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraRolePropScene) ? 1 : 0;
    }

    /**
     * 修改角色道具场景
     *
     * @param sakuraRolePropScene 角色道具场景
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSakuraRolePropScene(SakuraRolePropScene sakuraRolePropScene)
    {
        if (sakuraRolePropScene == null || sakuraRolePropScene.getId() == null)
        {
            log.info("后台更新资产失败，资产ID为空");
            throw new ServiceException("资产不能为空");
        }
        LambdaQueryWrapper<SakuraRolePropScene> storedQuery = Wrappers.lambdaQuery();
        storedQuery.eq(SakuraRolePropScene::getId, sakuraRolePropScene.getId());
        storedQuery.last("FOR UPDATE");
        SakuraRolePropScene stored = this.getOne(storedQuery, false);
        if (stored == null)
        {
            return 0;
        }
        if (Objects.equals(DEL_FLAG_DELETED, stored.getDelFlag())
                && Objects.equals(DELETE_REASON_AUTO_OVERWRITE, stored.getDeleteReason()))
        {
            log.info("后台更新自动覆盖资产墓碑被拒绝: assetId={}", stored.getId());
            throw new ServiceException("资产不可恢复");
        }
        validateImmutableIdentity(stored, sakuraRolePropScene);
        Long effectiveProjectId = sakuraRolePropScene.getProjectId() == null
                ? stored.getProjectId() : sakuraRolePropScene.getProjectId();
        Long effectiveUserId = sakuraRolePropScene.getUserId() == null
                ? stored.getUserId() : sakuraRolePropScene.getUserId();
        String effectiveAssetType = sakuraRolePropScene.getAssetType() == null
                ? stored.getAssetType() : sakuraRolePropScene.getAssetType();
        String effectiveName = sakuraRolePropScene.getName() == null
                ? stored.getName() : sakuraRolePropScene.getName();
        validateActiveNameAvailable(effectiveProjectId, effectiveUserId, effectiveAssetType,
                effectiveName, stored.getId());
        if (sakuraRolePropScene.getName() != null)
        {
            sakuraRolePropScene.setNameNormalized(normalizeAssetName(sakuraRolePropScene.getName()));
        }
        sakuraRolePropScene.setUpdateTime(DateUtils.getNowDate());
        try
        {
            if (!this.update(sakuraRolePropScene, Wrappers.<SakuraRolePropScene>lambdaUpdate()
                    .eq(SakuraRolePropScene::getId, stored.getId())
                    .eq(SakuraRolePropScene::getDelFlag, DEL_FLAG_NORMAL)))
            {
                return 0;
            }
        }
        catch (DuplicateKeyException e)
        {
            log.info("后台更新有效资产发生名称并发冲突: assetId={}, projectId={}, userId={}, assetType={}",
                    stored.getId(), effectiveProjectId, effectiveUserId, effectiveAssetType);
            throw new ServiceException("名称已存在");
        }
        if (Objects.equals(ASSET_TYPE_SCENE, stored.getAssetType())
                && sakuraRolePropScene.getName() != null
                && !Objects.equals(stored.getName(), sakuraRolePropScene.getName()))
        {
            int count = storyboardSceneSnapshotService.synchronizeSceneName(
                    stored.getProjectId(), stored.getUserId(), stored.getName(), sakuraRolePropScene.getName());
            log.info("后台场景改名已同步分镜快照: assetId={}, count={}", stored.getId(), count);
        }
        return 1;
    }

    private void validateImmutableIdentity(SakuraRolePropScene stored, SakuraRolePropScene update)
    {
        boolean changed = update.getProjectId() != null
                && !Objects.equals(stored.getProjectId(), update.getProjectId());
        changed = changed || update.getEpisodeId() != null
                && !Objects.equals(stored.getEpisodeId(), update.getEpisodeId());
        changed = changed || update.getUserId() != null
                && !Objects.equals(stored.getUserId(), update.getUserId());
        changed = changed || update.getAssetType() != null
                && !Objects.equals(stored.getAssetType(), update.getAssetType());
        changed = changed || update.getCreateSource() != null
                && !Objects.equals(stored.getCreateSource(), update.getCreateSource());
        changed = changed || update.getDelFlag() != null
                && !Objects.equals(stored.getDelFlag(), update.getDelFlag());
        changed = changed || update.getDeleteReason() != null
                || update.getDeletedAt() != null || update.getDeleteTaskId() != null;
        if (changed)
        {
            log.info("后台更新资产不可变身份被拒绝: assetId={}", stored.getId());
            throw new ServiceException("资产归属不可改");
        }
        if (!Objects.equals(DEL_FLAG_NORMAL, stored.getDelFlag()))
        {
            log.info("后台更新已删除资产被拒绝: assetId={}, delFlag={}", stored.getId(), stored.getDelFlag());
            throw new ServiceException("资产已删除");
        }
    }

    /**
     * 批量删除角色道具场景
     *
     * @param ids 需要删除的角色道具场景主键
     * @return 结果
     */
    @Override
    public int deleteSakuraRolePropSceneByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除角色道具场景信息
     *
     * @param id 角色道具场景主键
     * @return 结果
     */
    @Override
    public int deleteSakuraRolePropSceneById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
