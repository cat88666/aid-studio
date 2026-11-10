package com.sakura.sakura.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraWechatNotifyLog;
import com.sakura.sakura.mapper.SakuraWechatNotifyLogMapper;
import com.sakura.sakura.service.ISakuraWechatNotifyLogService;

/**
 * 微信模板消息推送日志业务实现
 */
@Service
public class SakuraWechatNotifyLogServiceImpl
        extends ServiceImpl<SakuraWechatNotifyLogMapper, SakuraWechatNotifyLog>
        implements ISakuraWechatNotifyLogService
{
}
