package com.sakura.sakura.service.impl;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraInviteCode;
import com.sakura.sakura.mapper.SakuraInviteCodeMapper;
import com.sakura.sakura.service.ISakuraInviteCodeService;

/**
 * 用户邀请码Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraInviteCodeServiceImpl extends ServiceImpl<SakuraInviteCodeMapper, SakuraInviteCode> implements ISakuraInviteCodeService
{
    /**
     * 按用户ID查询邀请码记录
     * 仅查询字段：id, userId, inviteCode（列表展示与绑定校验均只需这三个字段）
     */
    @Override
    public SakuraInviteCode getByUserId(Long userId)
    {
        if (Objects.isNull(userId))
        {
            return null;
        }
        return this.getOne(Wrappers.<SakuraInviteCode>lambdaQuery()
                .select(SakuraInviteCode::getId, SakuraInviteCode::getUserId, SakuraInviteCode::getInviteCode)
                .eq(SakuraInviteCode::getUserId, userId)
                .eq(SakuraInviteCode::getDelFlag, "0")
                .last("LIMIT 1"));
    }

    /**
     * 按邀请码查询记录
     * 仅查询字段：id, userId, inviteCode
     */
    @Override
    public SakuraInviteCode getByCode(String inviteCode)
    {
        return this.getOne(Wrappers.<SakuraInviteCode>lambdaQuery()
                .select(SakuraInviteCode::getId, SakuraInviteCode::getUserId, SakuraInviteCode::getInviteCode)
                .eq(SakuraInviteCode::getInviteCode, inviteCode)
                .eq(SakuraInviteCode::getDelFlag, "0")
                .last("LIMIT 1"));
    }
}
