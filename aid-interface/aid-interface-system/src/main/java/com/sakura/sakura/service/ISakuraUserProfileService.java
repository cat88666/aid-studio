package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraUserProfile;
import com.sakura.sakura.domain.vo.SakuraUserProfileVo;

/**
 * 用户扩展信息Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraUserProfileService extends IService<SakuraUserProfile>
{
    /**
     * 查询用户扩展信息
     *
     * @param id 用户扩展信息主键
     * @return 用户扩展信息
     */
    public SakuraUserProfile selectSakuraUserProfileById(Long id);

    /**
     * 查询用户扩展信息列表
     *
     * @param sakuraUserProfile 用户扩展信息
     * @return 用户扩展信息集合
     */
    public List<SakuraUserProfile> selectSakuraUserProfileList(SakuraUserProfile sakuraUserProfile);

    /**
     * 新增用户扩展信息
     *
     * @param sakuraUserProfile 用户扩展信息
     * @return 结果
     */
    public int insertSakuraUserProfile(SakuraUserProfile sakuraUserProfile);

    /**
     * 修改用户扩展信息
     *
     * @param sakuraUserProfile 用户扩展信息
     * @return 结果
     */
    public int updateSakuraUserProfile(SakuraUserProfile sakuraUserProfile);

    /**
     * 批量删除用户扩展信息
     *
     * @param ids 需要删除的用户扩展信息主键集合
     * @return 结果
     */
    public int deleteSakuraUserProfileByIds(Long[] ids);

    /**
     * 删除用户扩展信息信息
     *
     * @param id 用户扩展信息主键
     * @return 结果
     */
    public int deleteSakuraUserProfileById(Long id);

    /**
     * 按 userId 查询用户扩展信息。
     *
     * @param userId 用户 ID
     * @return 命中返回实体；否则返回 null
     */
    SakuraUserProfile getByUserId(Long userId);

    /**
     * 按 userId 查询用户账户积分信息。
     *
     * 仅查询余额、冻结余额、累计充值和累计消费字段，供 C 端余额快捷接口使用。
     *
     * @param userId 用户 ID
     * @return 命中返回账户积分信息；否则返回 null
     */
    SakuraUserProfile getAccountBalanceByUserId(Long userId);

    /**
     * 关联 sys_user 查询用户聚合信息列表（后台用户管理）。
     *
     * @param query 查询条件（userId/会员等级/实名/昵称/手机号/账号/状态）
     * @return 聚合用户信息列表
     */
    List<SakuraUserProfileVo> selectUserProfileVoList(SakuraUserProfileVo query);
}
