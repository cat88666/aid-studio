package com.sakura.tokendance.provider.image;

import com.sakura.domain.vo.AiModelConfigVo;
import com.sakura.media.dto.MediaImageGenerateRequest;

import java.util.Map;

/** TokenDance 图片协议的非 Spring 策略，避免 providerCode 强路由多命中。 */
interface TokenDanceImageProtocolStrategy
{
    String protocol();

    String endpoint();

    Map<String, Object> buildBody(AiModelConfigVo modelConfig, MediaImageGenerateRequest request);
}
