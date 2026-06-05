package com.alenwifidata.core.config.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * 数据库连接池调优配置
 *
 * 中等规模(500房间/1000在线): pool=20
 * 大型规模(2000+房间): pool=50, 启用读写分离
 */
@Configuration
public class DataSourceTuningConfig {

    /**
     * 生产环境: 调优连接池参数
     */
    @Bean
    @Profile("production")
    @ConditionalOnProperty(name = "spring.datasource.hikari.tuned", havingValue = "true")
    public DataSource productionDataSource(HikariConfig baseConfig) {
        HikariConfig config = new HikariConfig();

        // 从 application-production.yml 读取
        config.setMinimumIdle(10);
        config.setMaximumPoolSize(50);
        config.setIdleTimeout(600000);          // 10分钟
        config.setMaxLifetime(1800000);         // 30分钟
        config.setConnectionTimeout(30000);     // 30秒
        config.setValidationTimeout(5000);

        // 连接泄漏检测
        config.setLeakDetectionThreshold(60000); // 60秒告警

        // 连接测试
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }

    /**
     * 读写分离数据源（预留）
     * 主库写、从库读 → 使用 AbstractRoutingDataSource
     */
    // @Bean
    // @Primary
    // public DataSource routingDataSource(DataSource writeDs, DataSource readDs) {
    //     Map<Object, Object> targetDataSources = new HashMap<>();
    //     targetDataSources.put("WRITE", writeDs);
    //     targetDataSources.put("READ", readDs);
    //
    //     RoutingDataSource routing = new RoutingDataSource();
    //     routing.setTargetDataSources(targetDataSources);
    //     routing.setDefaultTargetDataSource(writeDs);
    //     return routing;
    // }
}
