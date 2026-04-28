package com.modelcore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 请求频率限制服务
 * <p>
 * 基于 Redis ZSET 实现滑动窗口限流，粒度为每个 API Key。
 * 基于 JDK 21 虚拟线程，使用同步 StringRedisTemplate。
 * 算法：以当前时间戳（毫秒）为 score，维护一个 1 分钟内的请求记录集合。
 * 每次请求时：
 *   1. 移除 1 分钟前的过期记录
 *   2. 统计当前窗口内请求数
 *   3. 若 >= 限制值则拒绝，返回 false
 *   4. 添加本次请求时间戳，并刷新 key 过期时间
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    /** 滑动窗口大小：1 分钟（毫秒） */
    private static final long WINDOW_MS = 60_000L;
    /** Key 过期时间：窗口 + 10 秒冗余，防止 Redis 内存堆积 */
    private static final Duration KEY_TTL = Duration.ofSeconds(70);

    private final StringRedisTemplate redisTemplate;

    /**
     * 检查指定 API Key 是否允许本次请求
     *
     * @param apiKeyId       API Key ID
     * @param limitPerMinute 每分钟最大请求数
     * @return true=允许，false=超出限制
     */
    public boolean isAllowed(Long apiKeyId, int limitPerMinute) {
        String key = RATE_LIMIT_KEY_PREFIX + apiKeyId;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW_MS;

        // 1. 移除 1 分钟前的过期记录
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 2. 统计当前窗口内请求数
        Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
        if (count != null && count >= limitPerMinute) {
            log.warn("API Key [{}] 触发限流：当前窗口请求数 {}，限制 {}/min",
                    apiKeyId, count, limitPerMinute);
            return false;
        }

        // 3. 记录本次请求（score = 时间戳，member 追加纳秒保证同毫秒内唯一）
        String member = now + "-" + System.nanoTime();
        redisTemplate.opsForZSet().add(key, member, now);

        // 4. 刷新 key 过期时间
        redisTemplate.expire(key, KEY_TTL);
        return true;
    }
}
