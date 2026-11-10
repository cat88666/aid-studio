package com.sakura;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author AID
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, RocketMQAutoConfiguration.class })
public class SakuraApplication
{
    public static void main(String[] args)
    {
        System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(SakuraApplication.class, args);
        System.out.println("启动成功");
    }
}
