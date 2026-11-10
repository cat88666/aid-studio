package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraRolePropSceneForm;

/**
 * 角色道具场景形态(从)Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraRolePropSceneFormService extends IService<SakuraRolePropSceneForm>
{
    /**
     * 查询角色道具场景形态(从)
     *
     * @param id 角色道具场景形态(从)主键
     * @return 角色道具场景形态(从)
     */
    public SakuraRolePropSceneForm selectSakuraRolePropSceneFormById(Long id);

    /**
     * 查询角色道具场景形态(从)列表
     *
     * @param sakuraRolePropSceneForm 角色道具场景形态(从)
     * @return 角色道具场景形态(从)集合
     */
    public List<SakuraRolePropSceneForm> selectSakuraRolePropSceneFormList(SakuraRolePropSceneForm sakuraRolePropSceneForm);

    /**
     * 新增角色道具场景形态(从)
     *
     * @param sakuraRolePropSceneForm 角色道具场景形态(从)
     * @return 结果
     */
    public int insertSakuraRolePropSceneForm(SakuraRolePropSceneForm sakuraRolePropSceneForm);

    /**
     * 修改角色道具场景形态(从)
     *
     * @param sakuraRolePropSceneForm 角色道具场景形态(从)
     * @return 结果
     */
    public int updateSakuraRolePropSceneForm(SakuraRolePropSceneForm sakuraRolePropSceneForm);

    /**
     * 批量删除角色道具场景形态(从)
     *
     * @param ids 需要删除的角色道具场景形态(从)主键集合
     * @return 结果
     */
    public int deleteSakuraRolePropSceneFormByIds(Long[] ids);

    /**
     * 删除角色道具场景形态(从)信息
     *
     * @param id 角色道具场景形态(从)主键
     * @return 结果
     */
    public int deleteSakuraRolePropSceneFormById(Long id);
}
