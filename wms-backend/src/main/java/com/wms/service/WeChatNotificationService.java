package com.wms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信通知服务 - 统一管理不同类型的微信消息发送
 */
@Service
public class WeChatNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WeChatNotificationService.class);

    @Autowired
    private WeChatMiniService weChatMiniService;

    @Autowired
    private WeChatOfficialService weChatOfficialService;

    @Autowired
    private WeChatWorkService weChatWorkService;

    @Value("${wechat.notification.enabled:true}")
    private boolean notificationEnabled;

    @Value("${wechat.notification.priority:mini,official,work}")
    private String priorityOrder;

    @Value("${wechat.notification.fallback:true}")
    private boolean fallbackEnabled;

    /**
     * 检查通知服务是否启用
     */
    public boolean isEnabled() {
        return notificationEnabled && (
            weChatMiniService.isEnabled() || 
            weChatOfficialService.isEnabled() || 
            weChatWorkService.isEnabled()
        );
    }

    /**
     * 发送消息 - 自动选择最佳发送方式
     * @param openid 用户标识
     * @param title 消息标题
     * @param content 消息内容
     * @param page 小程序页面路径
     * @param url 跳转链接
     * @return 发送结果
     */
    public boolean sendMessage(String openid, String title, String content, String page, String url) {
        if (!notificationEnabled) {
            log.warn("微信通知服务已禁用");
            return false;
        }

        log.info("开始发送微信消息, openid: {}, title: {}", openid, title);

        // 按优先级尝试发送
        String[] priorities = priorityOrder.split(",");
        boolean success = false;

        for (String priority : priorities) {
            priority = priority.trim();
            
            switch (priority) {
                case "mini":
                    if (weChatMiniService.isEnabled()) {
                        success = sendMiniMessage(openid, title, content, page);
                        if (success) {
                            log.info("小程序订阅消息发送成功");
                            return true;
                        } else if (!fallbackEnabled) {
                            return false;
                        }
                    }
                    break;
                    
                case "official":
                    if (weChatOfficialService.isEnabled()) {
                        success = sendOfficialMessage(openid, title, content, url);
                        if (success) {
                            log.info("公众号模板消息发送成功");
                            return true;
                        } else if (!fallbackEnabled) {
                            return false;
                        }
                    }
                    break;
                    
                case "work":
                    if (weChatWorkService.isEnabled()) {
                        success = sendWorkMessage(openid, title, content);
                        if (success) {
                            log.info("企业微信消息发送成功");
                            return true;
                        } else if (!fallbackEnabled) {
                            return false;
                        }
                    }
                    break;
                    
                default:
                    log.warn("未知的消息发送优先级: {}", priority);
                    break;
            }
        }

        log.error("所有微信消息发送方式都失败了");
        return false;
    }

    /**
     * 发送小程序订阅消息
     */
    private boolean sendMiniMessage(String openid, String title, String content, String page) {
        try {
            return weChatMiniService.sendSubscribeMessage(openid, title, content, page);
        } catch (Exception e) {
            log.error("发送小程序订阅消息异常", e);
            return false;
        }
    }

    /**
     * 发送公众号模板消息
     */
    private boolean sendOfficialMessage(String openid, String title, String content, String url) {
        try {
            return weChatOfficialService.sendTemplateMessage(openid, title, content, url);
        } catch (Exception e) {
            log.error("发送公众号模板消息异常", e);
            return false;
        }
    }

    /**
     * 发送企业微信消息
     */
    private boolean sendWorkMessage(String userId, String title, String content) {
        try {
            String message = title + "\n\n" + content;
            return weChatWorkService.sendTextMessage(userId, message);
        } catch (Exception e) {
            log.error("发送企业微信消息异常", e);
            return false;
        }
    }

    /**
     * 发送日志告警消息
     */
    public boolean sendLogAlert(String openid, String level, String message) {
        String title = "系统日志告警";
        String content = String.format("告警级别：%s\n告警内容：%s\n时间：%s", 
            level, message, java.time.LocalDateTime.now().toString());
        
        return sendMessage(openid, title, content, "pages/developer-tools/developer-tools", null);
    }

    /**
     * 发送每日报告
     */
    public boolean sendDailyReport(String openid, Map<String, Object> reportData) {
        String title = "每日统计报告";
        String content = String.format(
            "日期：%s\n计件数量：%s件\n总金额：¥%s\n库存变动：%s项\n\n详情请查看小程序",
            reportData.getOrDefault("date", "今日"),
            reportData.getOrDefault("pieceworkCount", 0),
            reportData.getOrDefault("totalAmount", "0.00"),
            reportData.getOrDefault("inventoryChanges", 0)
        );
        
        return sendMessage(openid, title, content, "pages/index/index", null);
    }

    /**
     * 发送系统告警
     */
    public boolean sendSystemAlert(String openid, String alertType, String description) {
        String title = "系统异常告警";
        String content = String.format("告警类型：%s\n描述：%s\n时间：%s\n\n请立即检查系统状态！", 
            alertType, description, java.time.LocalDateTime.now().toString());
        
        return sendMessage(openid, title, content, "pages/developer-tools/developer-tools", null);
    }

    /**
     * 发送库存告警
     */
    public boolean sendInventoryAlert(String openid, String productName, int currentStock, int threshold) {
        String title = "库存告警";
        String content = String.format(
            "产品：%s\n当前库存：%d\n告警阈值：%d\n时间：%s\n\n请及时补货！",
            productName, currentStock, threshold, java.time.LocalDateTime.now().toString()
        );
        
        return sendMessage(openid, title, content, "pages/inventory/inventory", null);
    }

    /**
     * 批量发送消息
     */
    public Map<String, Boolean> sendBatchMessage(String[] openids, String title, String content, String page, String url) {
        Map<String, Boolean> results = new HashMap<>();
        
        for (String openid : openids) {
            boolean success = sendMessage(openid, title, content, page, url);
            results.put(openid, success);
        }
        
        return results;
    }

    /**
     * 获取服务状态
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("enabled", notificationEnabled);
        status.put("miniService", weChatMiniService.isEnabled());
        status.put("officialService", weChatOfficialService.isEnabled());
        status.put("workService", weChatWorkService.isEnabled());
        status.put("priorityOrder", priorityOrder);
        status.put("fallbackEnabled", fallbackEnabled);
        
        return status;
    }

    /**
     * 测试通知功能
     */
    public boolean testNotification(String openid) {
        String title = "系统测试通知";
        String content = "这是一条测试消息，用于验证微信通知功能是否正常工作。\n发送时间：" + 
            java.time.LocalDateTime.now().toString();
        
        return sendMessage(openid, title, content, "pages/index/index", null);
    }
}