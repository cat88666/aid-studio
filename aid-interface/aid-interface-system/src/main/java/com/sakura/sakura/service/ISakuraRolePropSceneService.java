package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraRolePropScene;

/**
 * 角色道具场景Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraRolePropSceneService extends IService<SakuraRolePropScene>
{
    /**
     * 生成资产名称业务键。
     *
     * @param name 展示名称
     * @return 名称业务键
     */
    String normalizeAssetName(String name);

    /**
     * 校验有效资产名称是否可用。
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param assetType 资产类型
     * @param name 展示名称
     * @param excludeId 更新时排除的资产ID
     */
    void validateActiveNameAvailable(Long projectId, Long userId, String assetType,
                                     String name, Long excludeId);

    /**
     * 查询角色道具场景
     *
     * @param id 角色道具场景主键
     * @return 角色道具场景
     */
    public SakuraRolePropScene selectSakuraRolePropSceneById(Long id);

    /**
     * 查询角色道具场景列表
     *
     * @param sakuraRolePropScene 角色道具场景
     * @return 角色道具场景集合
     */
    public List<SakuraRolePropScene> selectSakuraRolePropSceneList(SakuraRolePropScene sakuraRolePropScene);

    /**
     * 新增角色道具场景
     *
     * @param sakuraRolePropScene 角色道具场景
     * @return 结果
     */
    public int insertSakuraRolePropScene(SakuraRolePropScene sakuraRolePropScene);

    /**
     * 修改角色道具场景
     *
     * @param sakuraRolePropScene 角色道具场景
     * @return 结果
     */
    public int updateSakuraRolePropScene(SakuraRolePropScene sakuraRolePropScene);

    /**
     * 批量删除角色道具场景
     *
     * @param ids 需要删除的角色道具场景主键集合
     * @return 结果
     */
    public int deleteSakuraRolePropSceneByIds(Long[] ids);

    /**
     * 删除角色道具场景信息
     *
     * @param id 角色道具场景主键
     * @return 结果
     */
    public int deleteSakuraRolePropSceneById(Long id);
}
