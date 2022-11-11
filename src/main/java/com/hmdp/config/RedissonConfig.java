package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Author: ww
 * DateTime: 2022/11/9 18:15
 * Description: Redisson配置类
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://114.115.177.60:6379")
                .setPassword("ww1103");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
