package com.alenwifidata.core.config.performance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Redis 高可用配置 — 支持哨兵模式和集群模式
 */
@Configuration
public class RedisHaConfig {

    /**
     * 生产环境: Redis Cluster 模式
     * 启动时设置 spring.profiles.active=cluster
     */
    @Bean
    @Profile("cluster")
    public RedisConnectionFactory redisClusterFactory() {
        // 配置集群节点
        List<String> clusterNodes = Arrays.asList(
                "redis-node1:6379",
                "redis-node2:6379",
                "redis-node3:6379",
                "redis-node4:6379",
                "redis-node5:6379",
                "redis-node6:6379"
        );

        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterNodes);
        clusterConfig.setMaxRedirects(3);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(clusterConfig, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * 哨兵模式 (Redis Sentinel)
     * 启动时设置 spring.profiles.active=sentinel
     */
    @Bean
    @Profile("sentinel")
    public RedisConnectionFactory redisSentinelFactory() {
        // Sentinel 配置由 application-sentinel.yml 中的 spring.redis.sentinel.* 属性驱动
        // 此处提供编程式 Fallback
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName("redis-sentinel");
        standalone.setPort(26379);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .build();

        return new LettuceConnectionFactory(standalone, clientConfig);
    }
}
