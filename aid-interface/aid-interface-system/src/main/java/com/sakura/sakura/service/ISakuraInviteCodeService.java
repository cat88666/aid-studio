package com.sakura.sakura.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraInviteCode;

/**
 * 用户邀请码Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraInviteCodeService extends IService<SakuraInviteCode>
{
    /**
     * 按用户ID查询邀请码记录
     *
     * @param userId 用户ID
     * @return 邀请码记录（不存在返回 null）
     */
    SakuraInviteCode getByUserId(Long userId);

    /**
     * 按邀请码查询记录（用于注册绑定/邀请码校验）
     *
     * @param inviteCode 邀请码（已归一化为大写）
     * @return 邀请码记录（不存在返回 null）
     */
    SakuraInviteCode getByCode(String inviteCode);
}
