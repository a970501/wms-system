package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.annotation.Auditable;
import com.wms.annotation.RequireRole;
import com.wms.common.BusinessException;
import com.wms.common.Result;
import com.wms.dto.ChangePasswordRequest;
import com.wms.dto.LoginRequest;
import com.wms.dto.RegisterRequest;
import com.wms.entity.User;
import com.wms.repository.UserRepository;
import com.wms.util.JwtUtil;
import com.wms.util.IpUtil;
import com.wms.service.LoginRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证控制器
 * 处理用户登录、注册、密码修改等认证相关操作
 */
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LoginRateLimiter rateLimiter;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 用户登录
     */
    @Auditable(module = "认证", action = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        // 检查是否被锁定
        if (rateLimiter.isLocked(username)) {
            long remaining = rateLimiter.getRemainingLockTime(username);
            return Result.error("账号已被锁定，请 " + (remaining / 60 + 1) + " 分钟后重试");
        }

        // 查找用户
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            rateLimiter.recordFailure(username);
            int remaining = rateLimiter.getRemainingAttempts(username);
            return Result.error("用户名或密码错误" + (remaining > 0 ? "，剩余 " + remaining + " 次尝试机会" : ""));
        }

        User user = userOpt.get();

        // 验证密码（支持BCrypt和明文密码的兼容模式）
        boolean passwordValid = false;
        boolean needMigration = false;

        // 检查是否是BCrypt加密的密码（以$2a$、$2b$或$2y$开头）
        if (user.getPassword().startsWith("$2a$") ||
            user.getPassword().startsWith("$2b$") ||
            user.getPassword().startsWith("$2y$")) {
            // BCrypt密码验证
            passwordValid = passwordEncoder.matches(password, user.getPassword());
        } else {
            // 明文密码验证（兼容旧数据）
            passwordValid = password.equals(user.getPassword());
            needMigration = true; // 标记需要迁移
        }

        if (!passwordValid) {
            rateLimiter.recordFailure(username);
            int remaining = rateLimiter.getRemainingAttempts(username);
            return Result.error("用户名或密码错误" + (remaining > 0 ? "，剩余 " + remaining + " 次尝试机会" : ""));
        }

        // 登录成功，清除失败记录
        rateLimiter.clearFailures(username);

        // 检查用户状态
        if (!"active".equals(user.getStatus())) {
            return Result.error("账号已被禁用，请联系管理员");
        }

        // 如果是明文密码，自动迁移到BCrypt
        if (needMigration) {
            user.setPassword(passwordEncoder.encode(password));
        }

        // 更新最后登录时间和IP
        user.setLastLogin(LocalDateTime.now());
        user.setLastLoginIp(IpUtil.getRealIp(request));
        userRepository.save(user);

        // 生成JWT token
        String token = "Bearer " + jwtUtil.generateToken(user.getUsername(), user.getRole());

        // 返回用户信息（隐藏密码）
        user.setPassword("******");

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);

        return Result.success(data);
    }
    
    /**
     * 用户注册
     */
    @Auditable(module = "认证", action = "用户注册")
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterRequest registerRequest, HttpServletRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new BusinessException("用户名已存在");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setRealName(registerRequest.getRealName());
        user.setEmail(registerRequest.getEmail());
        user.setPhone(registerRequest.getPhone());

        // 【安全加固】强制新用户为普通角色，忽略前端传来的role参数
        user.setRole("USER");
        user.setStatus("active");

        // 【安全加固】使用BCrypt加密密码
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        // 记录注册IP
        user.setLastLoginIp(IpUtil.getRealIp(request));

        // 保存用户
        User saved = userRepository.save(user);
        saved.setPassword("******");

        return Result.success(saved);
    }
    
    /**
     * 获取当前用户信息
     * 需要认证
     */
    @GetMapping("/me")
    @RequireAuth
    public Result<User> getCurrentUser(HttpServletRequest request) {
        // 从request中获取拦截器设置的用户名
        String username = (String) request.getAttribute("username");

        if (username == null) {
            throw new BusinessException("未登录");
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            throw new BusinessException("用户不存在");
        }

        User user = userOpt.get();
        user.setPassword("******");
        return Result.success(user);
    }
    
    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 简化版本，实际应清除token
        return Result.success();
    }
    
    /**
     * 修改密码
     * 需要认证
     */
    @PostMapping("/change-password")
    @RequireAuth
    public Result<Void> changePassword(HttpServletRequest request,
                                       @Valid @RequestBody ChangePasswordRequest passwordRequest) {
        // 从request中获取拦截器设置的用户名
        String username = (String) request.getAttribute("username");

        if (username == null) {
            throw new BusinessException("未登录");
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            throw new BusinessException("用户不存在");
        }

        User user = userOpt.get();

        // 验证旧密码（支持BCrypt和明文密码）
        boolean oldPasswordValid = false;
        if (user.getPassword().startsWith("$2a$") ||
            user.getPassword().startsWith("$2b$") ||
            user.getPassword().startsWith("$2y$")) {
            // BCrypt密码验证
            oldPasswordValid = passwordEncoder.matches(passwordRequest.getOldPassword(), user.getPassword());
        } else {
            // 明文密码验证（兼容旧数据）
            oldPasswordValid = passwordRequest.getOldPassword().equals(user.getPassword());
        }

        if (!oldPasswordValid) {
            throw new BusinessException("原密码错误");
        }

        // 更新密码（使用BCrypt加密）
        user.setPassword(passwordEncoder.encode(passwordRequest.getNewPassword()));
        userRepository.save(user);

        return Result.success();
    }
    
}
