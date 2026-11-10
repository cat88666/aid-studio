package com.sakura.common.sakura.encrypt.core.encryptor;

import com.sakura.common.sakura.encrypt.core.EncryptContext;
import com.sakura.common.sakura.encrypt.core.IEncryptor;

/**
 * 所有加密执行者的基类
 *
 * @author 视觉AID
 *
 */
public abstract class AbstractEncryptor implements IEncryptor {

    public AbstractEncryptor(EncryptContext context) {
        // 用户配置校验与配置注入
    }

}
