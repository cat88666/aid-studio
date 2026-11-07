package com.aid.auth.google;

import com.aid.aid.domain.AidUserSocial;
import com.aid.aid.mapper.AidUserSocialMapper;
import com.aid.aid.service.IAccountCancellationService;
import com.aid.auth.domain.dto.LoginRequest;
import com.aid.auth.strategy.impl.GoogleLoginStrategy;
import com.aid.common.core.domain.entity.SysUser;
import com.aid.common.exception.ServiceException;
import com.aid.core.service.ISysUserService;
import com.aid.promotion.service.IInviteService;
import com.aid.promotion.service.IRegisterBonusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleLoginStrategyTest {
    GoogleLoginChallenge challenge = mock(GoogleLoginChallenge.class);
    GoogleCredentialVerifier verifier = mock(GoogleCredentialVerifier.class);
    MockHttpServletRequest servlet = new MockHttpServletRequest();
    AidUserSocialMapper socials = mock(AidUserSocialMapper.class);
    ISysUserService users = mock(ISysUserService.class);
    IAccountCancellationService cancellations = mock(IAccountCancellationService.class);
    IInviteService invites = mock(IInviteService.class);
    IRegisterBonusService bonuses = mock(IRegisterBonusService.class);
    GoogleLoginStrategy strategy = new GoogleLoginStrategy(challenge, verifier, servlet, socials, users, cancellations, invites, bonuses);
    LoginRequest request = new LoginRequest();
    @org.junit.jupiter.api.BeforeAll static void prepareAuditExecutor() {
        var factory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        factory.registerSingleton("scheduledExecutorService", mock(java.util.concurrent.ScheduledExecutorService.class));
        new com.aid.common.utils.spring.SpringUtils().postProcessBeanFactory(factory);
    }
    @org.junit.jupiter.api.AfterEach void resetRequest() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
    @BeforeEach void setup() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(servlet));
        request.setCredential("credential"); request.setGoogleNonce("nonce");
        when(verifier.verify("credential", "nonce")).thenReturn(new GoogleCredentialVerifier.Identity("sub", "hello@gmail.com"));
    }
    @Test void invalidChallengeDoesNotReachGoogleOrDatabase() {
        doThrow(new ServiceException("expired")).when(challenge).validate(servlet, "nonce");
        assertThrows(ServiceException.class, () -> strategy.login(request));
        verifyNoInteractions(verifier, socials, users);
    }
    @Test void existingEmailNeverAutomaticallyLinks() {
        when(users.selectUserByEmail("hello@gmail.com")).thenReturn(new SysUser());
        assertThrows(ServiceException.class, () -> strategy.login(request));
        verify(users, never()).insertUser(any()); verify(socials, never()).insert(any(AidUserSocial.class));
        verifyNoInteractions(invites, bonuses);
    }
    @Test void disabledOrDeletedAccountCannotLogin() {
        AidUserSocial binding = new AidUserSocial(); binding.setUserId(5L); binding.setDelFlag("0");
        when(socials.selectOne(any())).thenReturn(binding);
        SysUser user = new SysUser(); user.setStatus("1"); user.setDelFlag("0");
        when(users.selectUserById(5L)).thenReturn(user);
        assertThrows(ServiceException.class, () -> strategy.login(request));
        user.setStatus("0"); user.setDelFlag("2");
        assertThrows(ServiceException.class, () -> strategy.login(request));
        verify(users, never()).updateLoginInfo(anyLong(), anyString(), any());
    }
    @Test void cancellationRestrictionPreventsRegistration() {
        doThrow(new ServiceException("cooling period")).when(cancellations).checkRegistrationAllowed("google_sub", "sub");
        assertThrows(ServiceException.class, () -> strategy.login(request));
        verify(users, never()).insertUser(any());
    }
    @Test void newIdentityCreatesOrdinaryUserAndSocialBinding() {
        request.setInviteCode("INVITE01");
        when(users.insertUser(any())).thenAnswer(call -> { ((SysUser)call.getArgument(0)).setUserId(9L); return 1; });
        when(socials.insert(any(AidUserSocial.class))).thenReturn(1);
        assertEquals(9L, strategy.login(request).getUserId());
        var user = org.mockito.ArgumentCaptor.forClass(SysUser.class);
        verify(users).insertUser(user.capture());
        assertArrayEquals(new Long[]{com.aid.auth.util.SilentRegistrationUtils.DEFAULT_ROLE_ID}, user.getValue().getRoleIds());
        assertTrue(user.getValue().getPassword().startsWith("$2"));
        assertNull(user.getValue().getPwdUpdateDate());
        var binding = org.mockito.ArgumentCaptor.forClass(AidUserSocial.class);
        verify(socials).insert(binding.capture());
        assertEquals("sub", binding.getValue().getOpenid());
        assertEquals("google", binding.getValue().getPlatformSource());
        assertNull(binding.getValue().getRawData());
        verify(invites).bindOnRegister(9L, "INVITE01", "google");
        verify(bonuses).grantAfterRegister(9L, "google");
    }
    @Test void existingIdentityReturnsSameAccountWithoutRegistration() {
        AidUserSocial binding = new AidUserSocial(); binding.setUserId(5L); binding.setDelFlag("0");
        when(socials.selectOne(any())).thenReturn(binding);
        SysUser user = new SysUser(); user.setUserId(5L); user.setUserName("existing"); user.setStatus("0"); user.setDelFlag("0");
        when(users.selectUserById(5L)).thenReturn(user);
        assertEquals(5L, strategy.login(request).getUserId());
        verify(users, never()).insertUser(any());
        verifyNoInteractions(invites, bonuses);
    }
    @Test void duplicateIdentityMarksSpringTransactionForRollback() {
        when(users.insertUser(any())).thenAnswer(call -> { ((SysUser)call.getArgument(0)).setUserId(9L); return 1; });
        when(socials.insert(any(AidUserSocial.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("private SQL"));
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        var interceptor = new org.springframework.transaction.interceptor.TransactionInterceptor(manager, new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        var factory = new org.springframework.aop.framework.ProxyFactory(strategy);
        factory.addAdvice(interceptor);
        var proxied = (com.aid.auth.strategy.LoginStrategy)factory.getProxy();
        var error = assertThrows(ServiceException.class, () -> proxied.login(request));
        assertFalse(error.getMessage().contains("SQL"));
        verify(manager).rollback(status); verify(manager, never()).commit(any());
        verifyNoInteractions(invites, bonuses);
    }
    @Test void credentialsExcludedFromDtoToString() {
        assertFalse(request.toString().contains("credential"));
        assertFalse(request.toString().contains("nonce"));
    }
}
