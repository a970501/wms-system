package com.wms.config;

import com.wms.annotation.Auditable;
import com.wms.entity.PieceWork;
import com.wms.service.AuditService;
import com.wms.util.IpUtil;
import com.wms.util.JwtUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class AuditLogAspect {

    @Autowired
    private AuditService auditService;

    @Autowired
    private JwtUtil jwtUtil;

    @Around("@annotation(com.wms.annotation.Auditable)")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);

        HttpServletRequest request = null;
        String username = "unknown";
        String ipAddress = "unknown";

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                request = attrs.getRequest();
                ipAddress = IpUtil.getRealIp(request);

                String token = request.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                    username = jwtUtil.getUsernameFromToken(token);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // 获取方法参数用于生成详细描述
        Object[] args = joinPoint.getArgs();
        String detailInfo = buildDetailInfo(auditable.action(), args, method);

        Object result = null;
        String status = "成功";
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            status = "失败: " + e.getMessage();
            throw e;
        } finally {
            try {
                String details = String.format("%s - 状态: %s", detailInfo, status);
                auditService.logSync(username, auditable.module(), auditable.action(), details, ipAddress);
            } catch (Exception e) {
                System.err.println("记录审计日志失败: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * 根据操作类型和参数构建详细信息
     */
    private String buildDetailInfo(String action, Object[] args, Method method) {
        StringBuilder details = new StringBuilder();
        
        try {
            // 针对计件管理的详细信息
            if (action.contains("计件")) {
                for (Object arg : args) {
                    if (arg instanceof PieceWork) {
                        PieceWork pw = (PieceWork) arg;
                        details.append("产品: ").append(pw.getProductName());
                        if (pw.getSpecification() != null) {
                            details.append(" | 规格: ").append(pw.getSpecification());
                        }
                        if (pw.getQuantity() != null) {
                            details.append(" | 数量: ").append(pw.getQuantity());
                        }
                        if (pw.getWorkerName() != null) {
                            details.append(" | 工人: ").append(pw.getWorkerName());
                        }
                        break;
                    } else if (arg instanceof Long && action.contains("删除")) {
                        details.append("记录ID: ").append(arg);
                    }
                }
            } else if (action.contains("删除")) {
                // 对于删除操作，记录ID
                for (Object arg : args) {
                    if (arg instanceof Long) {
                        details.append("记录ID: ").append(arg);
                        break;
                    }
                }
            }
            
            // 如果没有详细信息，使用方法名
            if (details.length() == 0) {
                details.append("操作: ").append(method.getName());
            }
        } catch (Exception e) {
            details.append("操作: ").append(action);
        }
        
        return details.toString();
    }
}
