package com.wms.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.annotation.RequireAuth;
import com.wms.annotation.RequireRole;
import com.wms.common.Result;
import com.wms.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

/**
 * JWT认证拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 处理OPTIONS预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 只处理Controller方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 检查方法或类上是否有@RequireAuth注解
        RequireAuth methodAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);
        RequireAuth classAuth = handlerMethod.getBeanType().getAnnotation(RequireAuth.class);

        // 如果没有@RequireAuth注解，或者value=false，则不需要认证
        if (methodAuth == null && classAuth == null) {
            return true;
        }

        if ((methodAuth != null && !methodAuth.value()) || (classAuth != null && !classAuth.value())) {
            return true;
        }

        // 获取token
        String token = request.getHeader("Authorization");
        if (token == null || token.trim().isEmpty()) {
            return unauthorized(response, "未登录或登录已过期");
        }

        // 去掉"Bearer "前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 验证token
        if (!jwtUtil.validateToken(token)) {
            return unauthorized(response, "Token无效或已过期");
        }

        // 获取用户信息
        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        if (username == null) {
            return unauthorized(response, "Token解析失败");
        }

        // 将用户信息存入request，供Controller使用
        request.setAttribute("username", username);
        request.setAttribute("role", role);

        // 检查角色权限
        RequireRole methodRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        RequireRole classRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);

        if (methodRole != null || classRole != null) {
            String[] requiredRoles = methodRole != null ? methodRole.value() : classRole.value();
            
            if (role == null || !Arrays.asList(requiredRoles).contains(role)) {
                return forbidden(response, "权限不足，需要角色: " + Arrays.toString(requiredRoles));
            }
        }

        return true;
    }

    /**
     * 返回401未授权
     */
    private boolean unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(message)));
        return false;
    }

    /**
     * 返回403禁止访问
     */
    private boolean forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(message)));
        return false;
    }
}

