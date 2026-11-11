package com.sakura.extensions.hpay;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "extensions.hpay", name = "enabled", havingValue = "true")
public class HPayScheduling { }
