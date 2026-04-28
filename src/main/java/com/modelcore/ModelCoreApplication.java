package com.modelcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ModelCore 主启动类
 * <p>
 * 企业级 AI API 网关与计费系统（B2B SaaS）
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class ModelCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelCoreApplication.class, args);
    }
}
