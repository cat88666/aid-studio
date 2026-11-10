package com.sakura.auth.service.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.sakura.auth.domain.vo.CryptoPublicKeyVO;
import com.sakura.auth.service.ApiCryptoService;
import com.sakura.common.sakura.crypto.config.ApiCryptoConfig;
import com.sakura.common.sakura.crypto.core.ApiCryptoConfigProvider;
import com.sakura.common.exception.ServiceException;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 接口加密辅助服务实现。
 *
 * 配置统一来自 {@link ApiCryptoConfigProvider}（数据库 sakura_config 动态加载）。
 * 用 {@link ObjectProvider} 惰性获取，避免特殊上下文缺少该 Bean 时装配失败。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCryptoServiceImpl implements ApiCryptoService {

    /** 加密配置提供者 */
    private final ObjectProvider<ApiCryptoConfigProvider> configProviderProvider;

    @Override
    public CryptoPublicKeyVO getPublicKey() {
        ApiCryptoConfigProvider provider = configProviderProvider.getIfAvailable();
        if (provider == null) {
            log.error("接口加密公钥下发失败: 配置提供者未装配");
            throw new ServiceException("密钥未配置");
        }
        ApiCryptoConfig config = provider.getConfig();
        // 未开启加密：明确告知前端无需加密，避免误判服务异常
        if (!config.isEnabled()) {
            log.info("接口加密公钥下发: 当前未开启接口加密");
            throw new ServiceException("未启用加密");
        }
        // 公钥缺失属配置错误：先记录再抛短文案
        if (StrUtil.isBlank(config.getPublicKey())) {
            log.error("接口加密公钥下发失败: 未配置 sakura_config:api_crypto.public_key");
            throw new ServiceException("密钥未配置");
        }
        // 仅下发公钥 + 服务端时间，私钥绝不外泄
        return new CryptoPublicKeyVO(config.getPublicKey(), System.currentTimeMillis());
    }
}
