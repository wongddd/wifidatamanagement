package com.alenwifidata.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 酒店 WiFi 数据管理计费系统 — 启动类
 */
@SpringBootApplication(scanBasePackages = "com.alenwifidata")
public class HotelWifiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelWifiApplication.class, args);
    }
}
