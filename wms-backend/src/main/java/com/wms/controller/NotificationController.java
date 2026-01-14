package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.annotation.RequireRole;
import com.wms.common.Result;
import com.wms.service.LogReportNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知管理控制器
 * 提供日志报告手动发送和配置状态查询
 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Autowired
    private LogReportNotificationService notificationService;

    /**
     * 获取通知配置状态
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @GetMapping("/status")
    public Result<?> getStatus() {
        Map<String, Object> status = notificationService.getNotificationStatus();
        return Result.success(status);
    }

    /**
     * 获取通知配置（完整配置信息）
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @GetMapping("/config")
    public Result<?> getConfig() {
        Map<String, Object> config = notificationService.getNotificationConfig();
        return Result.success(config);
    }

    /**
     * 更新通知配置
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/config")
    public Result<?> updateConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = notificationService.updateConfig(config);
        if ((boolean) result.getOrDefault("success", false)) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.getOrDefault("message", "配置更新失败"));
        }
    }

    /**
     * 手动发送日志报告
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/send-report")
    public Result<?> sendReport() {
        Map<String, Object> result = notificationService.sendManualReport();
        if ((boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.get("message"));
        }
    }

    /**
     * 手动发送月度计件统计报告
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/send-piecework-report")
    public Result<?> sendPieceworkReport() {
        Map<String, Object> result = notificationService.sendManualPieceworkReport();
        if ((boolean) result.getOrDefault("success", false)) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.getOrDefault("message", "发送失败"));
        }
    }

    /**
     * 发送日志报告到企业微信应用
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/send-wechat-work")
    public Result<?> sendWeChatWork(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        
        if (userId == null || userId.trim().isEmpty()) {
            return Result.error(400, "userId参数不能为空");
        }
        
        Map<String, Object> result = notificationService.sendReportToWeChatWork(userId);
        if ((boolean) result.getOrDefault("success", false)) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.get("message"));
        }
    }

    /**
     * 发送日志报告到微信公众号
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/send-wechat-official")
    public Result<?> sendWeChatOfficial(@RequestBody Map<String, String> request) {
        String openId = request.get("openId");
        String url = request.get("url");
        
        if (openId == null || openId.trim().isEmpty()) {
            return Result.error(400, "openId参数不能为空");
        }
        
        Map<String, Object> result = notificationService.sendReportToWeChatOfficial(openId, url);
        if ((boolean) result.getOrDefault("success", false)) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.get("message"));
        }
    }

    /**
     * 获取邮件接收者列表
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @GetMapping("/email-recipients")
    public Result<?> getEmailRecipients() {
        Map<String, Object> result = notificationService.getEmailRecipients();
        return Result.success(result);
    }

    /**
     * 更新邮件接收者列表
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/email-recipients")
    public Result<?> updateEmailRecipients(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = notificationService.updateEmailRecipients(request);
        if ((boolean) result.getOrDefault("success", false)) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.getOrDefault("message", "更新失败"));
        }
    }

    /**
     * 发送日志报告到微信小程序
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/send-wechat-mini")
    public Result<?> sendWeChatMini(@RequestBody Map<String, String> request) {
        String openId = request.get("openId");
        String page = request.get("page");
        
        if (openId == null || openId.trim().isEmpty()) {
            return Result.error(400, "openId参数不能为空");
        }
        
        Map<String, Object> result = notificationService.sendReportToWeChatMini(openId, page);
        if ((boolean) result.getOrDefault("success", false)) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.get("message"));
        }
    }

    /**
     * 获取消息类型设置
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @GetMapping("/message-types")
    public Result<?> getMessageTypes() {
        Map<String, Object> result = notificationService.getMessageTypes();
        return Result.success(result);
    }

    /**
     * 更新消息类型设置
     */
    @RequireAuth
    @RequireRole("ADMIN")
    @PostMapping("/message-types")
    public Result<?> updateMessageTypes(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = notificationService.updateMessageTypes(request);
        if ((boolean) result.getOrDefault("success", false)) {
            return Result.success(result);
        } else {
            return Result.error(400, (String) result.getOrDefault("message", "更新消息类型设置失败"));
        }
    }
}
