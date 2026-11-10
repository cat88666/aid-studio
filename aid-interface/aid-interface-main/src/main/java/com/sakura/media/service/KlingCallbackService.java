package com.sakura.media.service;

import com.sakura.media.dto.KlingCallbackContext;

/** 可灵媒体任务回调处理服务。 */
public interface KlingCallbackService {
    KlingCallbackResult handleKlingCallback(String rawBody, KlingCallbackContext context);
}
