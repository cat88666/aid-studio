package com.aid.auth.strategy.impl;

import com.aid.aid.domain.AidUserSocial;
import com.aid.aid.mapper.AidUserSocialMapper;
import com.aid.aid.service.IAccountCancellationService;
import com.aid.auth.domain.dto.LoginRequest;
import com.aid.auth.google.GoogleCredentialVerifier;
import com.aid.auth.google.GoogleLoginChallenge;
import com.aid.auth.strategy.LoginStrategy;
import com.aid.auth.util.SilentRegistrationUtils;
import com.aid.common.constant.AccountCancellationConstants;
import com.aid.common.constant.Constants;
import com.aid.framework.manager.AsyncManager;
import com.aid.framework.manager.factory.AsyncFactory;
import org.springframework.dao.DuplicateKeyException;
import com.aid.common.core.domain.entity.SysUser;
import com.aid.common.core.domain.model.LoginUser;
import com.aid.common.exception.ServiceException;
import com.aid.common.utils.SecurityUtils;
import com.aid.common.utils.ip.IpUtils;
import com.aid.core.service.ISysUserService;
import com.aid.promotion.service.IInviteService;
import com.aid.promotion.service.IRegisterBonusService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class GoogleLoginStrategy implements LoginStrategy {
    private final GoogleLoginChallenge challenge;
    private final GoogleCredentialVerifier verifier;
    private final HttpServletRequest servletRequest;
    private final AidUserSocialMapper socialMapper;
    private final ISysUserService users;
    private final IAccountCancellationService cancellations;
    private final IInviteService invites;
    private final IRegisterBonusService bonuses;

    @Override public String getLoginType() { return "google"; }
    @Override public void validate(LoginRequest request) {
        challenge.validate(servletRequest, request.getGoogleNonce());
        if (request.getCredential() == null || request.getCredential().isBlank() || request.getCredential().length() > 16000) {
            throw new ServiceException("Google登录凭证无效");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginUser login(LoginRequest request) {
        validate(request);
        GoogleCredentialVerifier.Identity identity = verifier.verify(request.getCredential(), request.getGoogleNonce());
        // Consume before any write. A rollback requires a fresh challenge, never replay a credential.
        challenge.consume(servletRequest, request.getGoogleNonce());
        AidUserSocial social = socialMapper.selectOne(new LambdaQueryWrapper<AidUserSocial>()
                .eq(AidUserSocial::getPlatformSource, "google").eq(AidUserSocial::getOpenid, identity.subject()));
        SysUser user;
        if (social != null) {
            if (!"0".equals(social.getDelFlag())) throw new ServiceException("账号已停用");
            user = users.selectUserById(social.getUserId());
            if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) {
                throw new ServiceException("账号已停用");
            }
        } else {
            cancellations.checkRegistrationAllowed(AccountCancellationConstants.IDENTITY_GOOGLE_SUB, identity.subject());
            if (identity.verifiedEmail() != null) {
                cancellations.checkRegistrationAllowed(AccountCancellationConstants.IDENTITY_EMAIL, identity.verifiedEmail());
                if (users.selectUserByEmail(identity.verifiedEmail()) != null) {
                    throw new ServiceException("该邮箱已有账户，请使用原有方式登录");
                }
            }
            user = createUser(identity.verifiedEmail());
            AidUserSocial binding = new AidUserSocial();
            binding.setUserId(user.getUserId());
            binding.setPlatformSource("google");
            binding.setOpenid(identity.subject());
            binding.setDelFlag("0");
            binding.setCreateBy("google");
            binding.setCreateTime(new Date());
            // Unique(platform_source, openid) prevents concurrent duplicate identities; failure rolls user back.
            try {
                if (socialMapper.insert(binding) != 1) throw new ServiceException("注册失败");
            } catch (DuplicateKeyException e) {
                throw new ServiceException("Google登录验证失败，请重试");
            }
            invites.bindOnRegister(user.getUserId(), request.getInviteCode(), "google");
            bonuses.grantAfterRegister(user.getUserId(), "google");
        }
        users.updateLoginInfo(user.getUserId(), IpUtils.getIpAddr(servletRequest), new Date());
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(user.getUserId(), user.getUserName(),
                Constants.LOGIN_SUCCESS, "Google登录成功"));
        LoginUser result = new LoginUser();
        result.setUser(user);
        result.setUserId(user.getUserId());
        return result;
    }

    private SysUser createUser(String email) {
        SysUser user = new SysUser();
        user.setUserName(SilentRegistrationUtils.generateUserName());
        user.setNickName(SilentRegistrationUtils.generateNickname());
        user.setEmail(email);
        user.setSex("2");
        user.setPassword(SecurityUtils.encryptPassword(SilentRegistrationUtils.generateUnusablePassword()));
        user.setStatus("0");
        user.setDelFlag("0");
        user.setDeptId(SilentRegistrationUtils.DEFAULT_DEPT_ID);
        user.setRoleIds(new Long[]{SilentRegistrationUtils.DEFAULT_ROLE_ID});
        user.setCreateBy("google");
        user.setCreateTime(new Date());
        if (users.insertUser(user) <= 0) throw new ServiceException("注册失败");
        return user;
    }
}
