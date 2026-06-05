package com.alenwifidata.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Portal 住客认证服务 — 独立 Spring Boot 应用
 * 默认端口 8081，与主 API 服务分开部署
 */
@SpringBootApplication(scanBasePackages = "com.alenwifidata")
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
