package com.sakura.notify.wechat.service;

import com.sakura.notify.wechat.vo.WechatTemplatePayload;
import com.sakura.notify.wechat.vo.WechatTemplateSendResult;

/**
 * 微信模板消息发送器。
 */
public interface IWechatTemplateMessageSender
{
    WechatTemplateSendResult send(WechatTemplatePayload payload);
}
