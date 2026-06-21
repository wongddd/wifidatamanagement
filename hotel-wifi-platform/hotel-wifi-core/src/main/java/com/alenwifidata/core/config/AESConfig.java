package com.alenwifidata.core.config;

import com.alenwifidata.common.util.AESCrypto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * AES 加密密钥初始化 —— 在 Spring Boot 启动时自动加载
 */
@Slf4j
@Configuration
public class AESConfig {

    @Value("${aes.secret-key:HotelWifi@2026.AES.256bit.Key!!}")
    private String secretKey;

    @PostConstruct
    public void init() {
        AESCrypto.init(secretKey);
        log.info("AESCrypto 已初始化 (AES-256-GCM)");
    }
}
