package com.wms.controller;

import com.wms.annotation.Auditable;
import com.wms.annotation.RequireAuth;
import com.wms.annotation.RequireRole;
import com.wms.common.Result;
import com.wms.entity.User;
import com.wms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequireAuth
@RequireRole({"ADMIN"})
public class UserController {
    
    @Autowired
    private UserRepository userRepository;
    
    @GetMapping
    public Result<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        // 隐藏密码
        users.forEach(u -> u.setPassword("******"));
        return Result.success(users);
    }
    
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            user.setPassword("******");
        }
        return Result.success(user);
    }
    
    @PostMapping
    public Result<User> createUser(@RequestBody User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return Result.error("用户名已存在");
        }
        
        // 这里应该使用BCrypt加密密码，简化版本直接保存
        // user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        
        User saved = userRepository.save(user);
        saved.setPassword("******");
        return Result.success(saved);
    }
    
    @Auditable(module = "用户管理", action = "更新用户")
    @PutMapping("/{id}")
    public Result<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        User existing = userRepository.findById(id).orElse(null);
        if (existing == null) {
            return Result.error("用户不存在");
        }
        
        // 更新字段（不更新密码）
        existing.setRealName(user.getRealName());
        existing.setEmail(user.getEmail());
        existing.setPhone(user.getPhone());
        existing.setRole(user.getRole());
        existing.setStatus(user.getStatus());
        
        User updated = userRepository.save(existing);
        updated.setPassword("******");
        return Result.success(updated);
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return Result.error("用户不存在");
        }
        userRepository.deleteById(id);
        return Result.success();
    }
    
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        String newPassword = body.get("password");
        // 这里应该使用BCrypt加密
        // user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        user.setPassword(newPassword);
        userRepository.save(user);
        return Result.success();
    }
    
    @PutMapping("/{id}/toggle-status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }

        user.setStatus("active".equals(user.getStatus()) ? "inactive" : "active");
        userRepository.save(user);
        return Result.success();
    }

    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return Result.error("用户不存在");
        }

        String role = body.get("role");
        if (role == null || (!role.equals("ADMIN") && !role.equals("USER") && !role.equals("operator"))) {
            return Result.error("角色必须是ADMIN、USER或operator");
        }

        user.setRole(role);
        userRepository.save(user);
        return Result.success();
    }
}
