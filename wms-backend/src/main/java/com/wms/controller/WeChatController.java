package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.annotation.RequireRole;
import com.wms.common.Result;
import com.wms.dto.WeChatMessageRequest;
import com.wms.service.WeChatMiniService;
import com.wms.service.WeChatNotificationService;
import com.wms.service.WeChatOfficialService;
import com.wms.service.WeChatWorkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信通知控制器
 */
@RestController
@RequestMapping("/api/wechat")
@RequireAuth
@Validated
public class WeChatController {

    private static final Logger log = LoggerFactory.getLogger(WeChatController.class);

    @Autowired
    private WeChatNotificationService weChatNotificationService;

    @Autowired
    private WeChatMiniService weChatMiniService;

    @Autowired
    private WeChatOfficialService weChatOfficialService;

    @Autowired
    private WeChatWorkService weChatWorkService;

    /**
     * 发送微信消息
     */
    @PostMapping("/send-message")
    @RequireRole("ADMIN")
    public Result<Map<String, Object>> sendMessage(@Valid @RequestBody WeChatMessageRequest request) {
        log.info("收到微信消息发送请求: {}", request);

        try {
            boolean success = weChatNotificationService.sendMessage(
                request.getOpenid(),
                request.getTitle(),
                request.getContent(),
                request.getPage(),
                request.getUrl()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "消息发送成功" : "消息发送失败");
            result.put("timestamp", request.getTimestamp());

            if (success) {
                log.info("微信消息发送成功, openid: {}", request.getOpenid());
                return Result.success(result);
            } else {
                log.warn("微信消息发送失败, openid: {}", request.getOpenid());
                return Result.error("消息发送失败");
            }

        } catch (Exception e) {
            log.error("发送微信消息异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "系统异常: " + e.getMessage());
            result.put("timestamp", request.getTimestamp());
            return Result.error("系统异常");
        }
    }

    /**
     * 发送订阅消息
     */
    @PostMapping("/send-subscribe-message")
    @RequireRole("ADMIN")
    public Result<Map<String, Object>> sendSubscribeMessage(@Valid @RequestBody WeChatMessageRequest request) {
        log.info("收到小程序订阅消息发送请求: {}", request);

        try {
            boolean success = weChatMiniService.sendSubscribeMessage(
                request.getOpenid(),
                request.getTitle(),
                request.getContent(),
                request.getPage()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "订阅消息发送成功" : "订阅消息发送失败");
            result.put("timestamp", request.getTimestamp());

            if (success) {
                log.info("小程序订阅消息发送成功, openid: {}", request.getOpenid());
                return Result.success(result);
            } else {
                log.warn("小程序订阅消息发送失败, openid: {}", request.getOpenid());
                return Result.error("订阅消息发送失败");
            }

        } catch (Exception e) {
            log.error("发送小程序订阅消息异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "系统异常: " + e.getMessage());
            result.put("timestamp", request.getTimestamp());
            return Result.error("系统异常");
        }
    }

    /**
     * 发送模板消息
     */
    @PostMapping("/send-template-message")
    @RequireRole("ADMIN")
    public Result<Map<String, Object>> sendTemplateMessage(@Valid @RequestBody WeChatMessageRequest request) {
        log.info("收到公众号模板消息发送请求: {}", request);

        try {
            boolean success = weChatOfficialService.sendTemplateMessage(
                request.getOpenid(),
                request.getTitle(),
                request.getContent(),
                request.getUrl()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "模板消息发送成功" : "模板消息发送失败");
            result.put("timestamp", request.getTimestamp());

            if (success) {
                log.info("公众号模板消息发送成功, openid: {}", request.getOpenid());
                return Result.success(result);
            } else {
                log.warn("公众号模板消息发送失败, openid: {}", request.getOpenid());
                return Result.error("模板消息发送失败");
            }

        } catch (Exception e) {
            log.error("发送公众号模板消息异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "系统异常: " + e.getMessage());
            result.put("timestamp", request.getTimestamp());
            return Result.error("系统异常");
        }
    }

    /**
     * 发送企业微信消息
     */
    @PostMapping("/send-work-message")
    @RequireRole("ADMIN")
    public Result<Map<String, Object>> sendWorkMessage(@Valid @RequestBody WeChatMessageRequest request) {
        log.info("收到企业微信消息发送请求: {}", request);

        try {
            // 企业微信使用用户ID而不是openid
            String userId = request.getOpenid(); // 这里复用openid字段传递userId
            String content = request.getTitle() + "\n\n" + request.getContent();
            
            boolean success = weChatWorkService.sendTextMessage(userId, content);

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "企业微信消息发送成功" : "企业微信消息发送失败");
            result.put("timestamp", request.getTimestamp());

            if (success) {
                log.info("企业微信消息发送成功, userId: {}", userId);
                return Result.success(result);
            } else {
                log.warn("企业微信消息发送失败, userId: {}", userId);
                return Result.error("企业微信消息发送失败");
            }

        } catch (Exception e) {
            log.error("发送企业微信消息异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "系统异常: " + e.getMessage());
            result.put("timestamp", request.getTimestamp());
            return Result.error("系统异常");
        }
    }

    /**
     * 获取微信服务状态
     */
    @GetMapping("/service-status")
    @RequireRole("ADMIN")
    public Result<Map<String, Object>> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("miniService", weChatMiniService.isEnabled());
        status.put("officialService", weChatOfficialService.isEnabled());
        status.put("workService", weChatWorkService.isEnabled());
        status.put("notificationService", weChatNotificationService.isEnabled());
        
        return Result.success(status);
    }

    /**
     * 测试微信通知功能
     */
    @PostMapping("/test-notification")
    @RequireRole("ADMIN")
    public Result<Map<String, Object>> testNotification(@RequestParam String openid) {
        log.info("测试微信通知功能, openid: {}", openid);

        try {
            WeChatMessageRequest testRequest = new WeChatMessageRequest();
            testRequest.setOpenid(openid);
            testRequest.setTitle("系统测试通知");
            testRequest.setContent("这是一条测试消息，用于验证微信通知功能是否正常工作。发送时间：" + 
                java.time.LocalDateTime.now().toString());
            testRequest.setPage("pages/index/index");

            boolean success = weChatNotificationService.sendMessage(
                testRequest.getOpenid(),
                testRequest.getTitle(),
                testRequest.getContent(),
                testRequest.getPage(),
                null
            );

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "测试消息发送成功" : "测试消息发送失败");
            result.put("timestamp", testRequest.getTimestamp());

            return success ? Result.success(result) : Result.error("测试失败");

        } catch (Exception e) {
            log.error("测试微信通知功能异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "测试异常: " + e.getMessage());
            return Result.error("测试异常");
        }
    }
}