package com.wms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录限流器单元测试
 */
class LoginRateLimiterTest {

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter();
    }

    @Test
    void testInitialState() {
        assertFalse(rateLimiter.isLocked("testuser"));
        assertEquals(5, rateLimiter.getRemainingAttempts("testuser"));
    }

    @Test
    void testRecordFailure() {
        rateLimiter.recordFailure("testuser");
        assertEquals(4, rateLimiter.getRemainingAttempts("testuser"));
        assertFalse(rateLimiter.isLocked("testuser"));
    }

    @Test
    void testLockAfterMaxAttempts() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure("testuser");
        }
        assertTrue(rateLimiter.isLocked("testuser"));
        assertEquals(0, rateLimiter.getRemainingAttempts("testuser"));
    }

    @Test
    void testClearFailures() {
        rateLimiter.recordFailure("testuser");
        rateLimiter.recordFailure("testuser");
        rateLimiter.clearFailures("testuser");
        assertEquals(5, rateLimiter.getRemainingAttempts("testuser"));
        assertFalse(rateLimiter.isLocked("testuser"));
    }

    @Test
    void testDifferentUsers() {
        rateLimiter.recordFailure("user1");
        rateLimiter.recordFailure("user1");
        assertEquals(3, rateLimiter.getRemainingAttempts("user1"));
        assertEquals(5, rateLimiter.getRemainingAttempts("user2"));
    }
}
