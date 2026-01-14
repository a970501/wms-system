package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import javax.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 255, message = "密码长度必须在6-255个字符之间")
    @Column(nullable = false)
    private String password;

    @Size(max = 50, message = "真实姓名长度不能超过50个字符")
    @Column(length = 50)
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    @Column(length = 100)
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Column(length = 20)
    private String phone;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(ADMIN|USER|operator)$", message = "角色必须是ADMIN、USER或operator")
    @Column(length = 20)
    private String role = "operator";

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(active|inactive|locked)$", message = "状态必须是active、inactive或locked")
    @Column(length = 20)
    private String status = "active";

    private LocalDateTime lastLogin;

    @Column(length = 50)
    private String lastLoginIp;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @Size(max = 50, message = "创建人长度不能超过50个字符")
    @Column(length = 50)
    private String createdBy;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
