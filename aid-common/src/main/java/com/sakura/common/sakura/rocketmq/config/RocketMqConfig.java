package com.sakura.common.sakura.rocketmq.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ配置类
 *
 * 通过RocketMqConfigManager动态获取配置
 * 运行时根据mqType选择消息队列实现(RocketMQ/Redis)
 *
 * @author 视觉AID
 */
@Configuration
@ComponentScan(basePackages = "com.sakura.common.sakura.rocketmq")
public class RocketMqConfig {

}
