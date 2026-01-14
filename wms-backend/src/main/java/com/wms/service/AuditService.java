package com.wms.service;

import com.wms.entity.AuditLog;
import com.wms.repository.AuditLogRepository;
import com.wms.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 审计日志服务
 */
@Service
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * 异步记录审计日志
     */
    @Async
    public void log(String username, String module, String action, String details, HttpServletRequest request) {
        try {
            AuditLog log = new AuditLog();
            log.setUsername(username != null ? username : "system");
            log.setModule(module);
            log.setAction(action);
            log.setDetails(details);
            log.setCreatedAt(LocalDateTime.now());
            
            if (request != null) {
                log.setIpAddress(IpUtil.getRealIp(request));
            }
            
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("记录审计日志失败: " + e.getMessage());
        }
    }

    /**
     * 同步记录审计日志
     */
    public void logSync(String username, String module, String action, String details, String ipAddress) {
        try {
            AuditLog log = new AuditLog();
            log.setUsername(username != null ? username : "system");
            log.setModule(module);
            log.setAction(action);
            log.setDetails(details);
            log.setCreatedAt(LocalDateTime.now());
            log.setIpAddress(ipAddress);
            
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("记录审计日志失败: " + e.getMessage());
        }
    }
}
