package com.modelcore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动信息打印器
 * <p>
 * 应用启动完成后，在控制台打印所有可访问的页面路径和 API 端点，方便开发调试。
 * </p>
 */
@Slf4j
@Component
@Order(100)
public class StartupInfoPrinter implements CommandLineRunner {

    @Value("${server.port:8080}")
    private int port;

    @Override
    public void run(String... args) {
        String base = "http://localhost:" + port;

        log.info("========================================");
        log.info("  ModelCore 启动成功！");
        log.info("========================================");
        log.info("");
        log.info("  【平台超管】默认账号: admin / admin123");
        log.info("");
        log.info("  ---- 公开页面 ----");
        log.info("  登录页:     {}/login", base);
        log.info("  注册页:     {}/register", base);
        log.info("");
        log.info("  ---- 超管后台（需 SUPER_ADMIN 登录）----");
        log.info("  租户管理:   {}/admin/tenants", base);
        log.info("");
        log.info("  ---- 租户后台（需租户账号登录）----");
        log.info("  仪表盘:     {}/dashboard", base);
        log.info("  API Key:    {}/keys", base);
        log.info("  调用日志:   {}/logs", base);
        log.info("  团队管理:   {}/users", base);
        log.info("");
        log.info("  ---- API 端点 ----");
        log.info("  聊天接口:   POST {}/v1/chat/completions", base);
        log.info("  (Header: Authorization: Bearer sk-xxx)");
        log.info("");
        log.info("  ---- 图表数据接口 ----");
        log.info("  每日调用量: GET {}/api/stats/daily-calls?days=7", base);
        log.info("  每日费用:   GET {}/api/stats/daily-costs?days=7", base);
        log.info("========================================");
    }
}
