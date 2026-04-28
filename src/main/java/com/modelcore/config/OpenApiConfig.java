package com.modelcore.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置
 * <p>
 * 访问地址：http://localhost:8080/swagger-ui.html
 * 使用方式：点击右上角 Authorize → 输入 sk-xxx → 即可测试 /v1/** 接口
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ModelCore AI API 网关")
                        .version("1.0.0")
                        .description("""
                                ## 企业级 AI API 管理与计费系统

                                ### 认证方式
                                本系统使用 **API Key** 认证，在请求头中携带：
                                ```
                                Authorization: Bearer sk-xxxxxxxxxxxxxxxxxxxxxxxx
                                ```

                                ### 接口兼容性
                                所有接口完全兼容 **OpenAI API 格式**，可直接替换 base_url 使用。

                                ### 快速开始
                                1. 登录后台管理 `/login`
                                2. 在「API Key 管理」中创建 Key
                                3. 点击右上角 **Authorize** 填入 Key
                                4. 即可在此页面直接测试接口
                                """)
                        .contact(new Contact()
                                .name("ModelCore")
                                .url("http://localhost:8080")))
                // 定义 Bearer Token 认证方案
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API Key")
                                .description("输入你的 API Key（sk- 开头），例如：sk-abc123...")))
                // 全局应用认证（所有接口默认需要认证）
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
