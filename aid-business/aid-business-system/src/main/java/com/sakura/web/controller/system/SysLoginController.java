package com.sakura.web.controller.system;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakura.sakura.domain.SakuraUserProfile;
import com.sakura.sakura.domain.SakuraUserSocial;
import com.sakura.sakura.mapper.SakuraUserProfileMapper;
import com.sakura.sakura.mapper.SakuraUserSocialMapper;
import com.sakura.sakura.domain.vo.UserSocialVO;
import com.sakura.sakura.service.ISakuraConfigService;
import com.sakura.common.annotation.Anonymous;
import com.sakura.common.constant.Constants;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.core.domain.entity.SysMenu;
import com.sakura.common.core.domain.entity.SysUser;
import com.sakura.common.core.domain.model.LoginBody;
import com.sakura.common.core.domain.model.LoginUser;
import com.sakura.common.core.text.Convert;
import com.sakura.common.utils.DateUtils;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.common.utils.StringUtils;
import com.sakura.framework.web.service.SysLoginService;
import com.sakura.framework.web.service.SysPermissionService;
import com.sakura.common.core.service.TokenService;
import com.sakura.core.service.ISysConfigService;
import com.sakura.core.service.ISysMenuService;
import cn.hutool.core.collection.CollUtil;

/**
 * 登录验证
 *
 * @author 视觉AID
 */
@RestController
public class SysLoginController {
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SakuraUserProfileMapper userProfileMapper;

    @Autowired
    private SakuraUserSocialMapper userSocialMapper;

    /** sakura_config：读取后台随机登录入口配置（admin_entry） */
    @Autowired
    private ISakuraConfigService sakuraConfigService;

    /** IP 限流：防止暴力尝试登录 / 访问码 */
    @Autowired
    private com.sakura.sakura.security.IpRateLimitGuard ipRateLimitGuard;

    /** 后台随机登录入口请求头：访问码 */
    private static final String HEADER_ENTRY_CODE = "X-Admin-Entry-Code";

    /**
     * 登录方法
     * 显式声明匿名访问；启用「后台随机登录入口」后，后端强制校验访问码，
     * 避免绕过前端随机路径直接请求 /login。
     *
     * @param loginBody 登录信息
     * @return 结果
     */
    @Anonymous
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody, HttpServletRequest request) {
        // IP 限流：单 IP 每分钟尝试次数由 sakura_config(admin_entry.rate_limit_per_min) 动态控制，默认 10
        if (!ipRateLimitGuard.allowByConfig("login", "admin_entry", "rate_limit_per_min", 10)) {
            return AjaxResult.error("操作过于频繁，请稍后再试");
        }
        // 后台随机登录入口：开启后必须携带正确访问码（请求头），否则拒绝（不泄露具体原因）
        if (adminEntryEnabled()) {
            String input = request.getHeader(HEADER_ENTRY_CODE);
            String real = adminEntryCode();
            String safeInput = input == null ? "" : input.trim();
            if (StringUtils.isEmpty(real) || !real.equals(safeInput)) {
                return AjaxResult.error("登录校验失败");
            }
        }
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    /** 后台随机登录入口是否启用（异常/缺失按未启用，避免误锁死登录） */
    private boolean adminEntryEnabled() {
        try {
            String v = sakuraConfigService.getConfigValue("admin_entry", "enabled");
            return "true".equalsIgnoreCase(v) || "Y".equalsIgnoreCase(v) || "1".equals(v);
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取后台登录访问码 */
    private String adminEntryCode() {
        try {
            return sakuraConfigService.getConfigValue("admin_entry", "access_code");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取用户信息
     *
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        if (!loginUser.getPermissions().equals(permissions)) {
            loginUser.setPermissions(permissions);
            tokenService.refreshToken(loginUser);
        }
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        ajax.put("isDefaultModifyPwd", initPasswordIsModify(user.getPwdUpdateDate()));
        ajax.put("isPasswordExpired", passwordIsExpiration(user.getPwdUpdateDate()));
        return ajax;
    }

    /**
     * 获取用户扩展信息
     */
    private SakuraUserProfile getUserProfile(Long userId) {
        LambdaQueryWrapper<SakuraUserProfile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SakuraUserProfile::getUserId, userId)
                .eq(SakuraUserProfile::getDelFlag, "0");
        return userProfileMapper.selectOne(queryWrapper);
    }

    /**
     * 获取用户社交账号列表
     */
    private List<UserSocialVO> getUserSocialList(Long userId) {
        LambdaQueryWrapper<SakuraUserSocial> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SakuraUserSocial::getUserId, userId)
                .eq(SakuraUserSocial::getDelFlag, "0");
        List<SakuraUserSocial> socialList = userSocialMapper.selectList(queryWrapper);

        if (CollUtil.isEmpty(socialList)) {
            return new ArrayList<>();
        }

        return socialList.stream()
                .map(social -> UserSocialVO.builder()
                        .platformSource(social.getPlatformSource())
                        .openid(social.getOpenid())
                        .unionid(social.getUnionid())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取路由信息
     *
     * @return 路由信息
     */
    @GetMapping(value = "getRouters", produces = "application/json;charset=UTF-8")
    public AjaxResult getRouters() {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }

    // 检查初始密码是否提醒修改
    public boolean initPasswordIsModify(Date pwdUpdateDate) {
        Integer initPasswordModify = Convert.toInt(configService.selectConfigByKey("sys.account.initPasswordModify"));
        return initPasswordModify != null && initPasswordModify == 1 && pwdUpdateDate == null;
    }

    // 检查密码是否过期
    public boolean passwordIsExpiration(Date pwdUpdateDate) {
        Integer passwordValidateDays = Convert.toInt(configService.selectConfigByKey("sys.account.passwordValidateDays"));
        if (passwordValidateDays != null && passwordValidateDays > 0) {
            if (StringUtils.isNull(pwdUpdateDate)) {
                // 如果从未修改过初始密码，直接提醒过期
                return true;
            }
            Date nowDate = DateUtils.getNowDate();
            return DateUtils.differentDaysByMillisecond(nowDate, pwdUpdateDate) > passwordValidateDays;
        }
        return false;
    }
}
