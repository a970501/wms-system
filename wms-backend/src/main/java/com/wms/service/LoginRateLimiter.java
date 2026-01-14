package com.wms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录限流器
 * 防止暴力破解攻击
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    // 最大尝试次数
    private static final int MAX_ATTEMPTS = 5;
    // 锁定时间（分钟）
    private static final int LOCK_DURATION_MINUTES = 15;

    // 存储失败记录：username -> FailedAttempt
    private final Map<String, FailedAttempt> failedAttempts = new ConcurrentHashMap<>();

    /**
     * 检查用户是否被锁定
     */
    public boolean isLocked(String username) {
        FailedAttempt attempt = failedAttempts.get(username);
        if (attempt == null) {
            return false;
        }

        // 检查锁定是否过期
        if (attempt.isExpired()) {
            failedAttempts.remove(username);
            return false;
        }

        return attempt.getAttempts() >= MAX_ATTEMPTS;
    }

    /**
     * 获取剩余锁定时间（秒）
     */
    public long getRemainingLockTime(String username) {
        FailedAttempt attempt = failedAttempts.get(username);
        if (attempt == null || attempt.isExpired()) {
            return 0;
        }
        return Math.max(0, (attempt.getLockUntil() - System.currentTimeMillis()) / 1000);
    }

    /**
     * 记录登录失败
     */
    public void recordFailure(String username) {
        FailedAttempt attempt = failedAttempts.computeIfAbsent(username, k -> new FailedAttempt());
        attempt.increment();

        if (attempt.getAttempts() >= MAX_ATTEMPTS) {
            attempt.setLockUntil(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(LOCK_DURATION_MINUTES));
            log.warn("用户 {} 因连续 {} 次登录失败被锁定 {} 分钟", username, MAX_ATTEMPTS, LOCK_DURATION_MINUTES);
        }
    }

    /**
     * 登录成功，清除失败记录
     */
    public void clearFailures(String username) {
        failedAttempts.remove(username);
    }

    /**
     * 获取剩余尝试次数
     */
    public int getRemainingAttempts(String username) {
        FailedAttempt attempt = failedAttempts.get(username);
        if (attempt == null || attempt.isExpired()) {
            return MAX_ATTEMPTS;
        }
        return Math.max(0, MAX_ATTEMPTS - attempt.getAttempts());
    }

    /**
     * 失败尝试记录
     */
    private static class FailedAttempt {
        private int attempts = 0;
        private long lockUntil = 0;
        private long lastAttempt = System.currentTimeMillis();

        public void increment() {
            // 如果距离上次尝试超过锁定时间，重置计数
            if (System.currentTimeMillis() - lastAttempt > TimeUnit.MINUTES.toMillis(LOCK_DURATION_MINUTES)) {
                attempts = 0;
                lockUntil = 0;
            }
            attempts++;
            lastAttempt = System.currentTimeMillis();
        }

        public int getAttempts() {
            return attempts;
        }

        public long getLockUntil() {
            return lockUntil;
        }

        public void setLockUntil(long lockUntil) {
            this.lockUntil = lockUntil;
        }

        public boolean isExpired() {
            return lockUntil > 0 && System.currentTimeMillis() > lockUntil;
        }
    }
}
