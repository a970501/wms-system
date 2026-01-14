package com.wms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.entity.AuditLog;
import com.wms.entity.PieceWork;
import com.wms.entity.User;
import com.wms.repository.AuditLogRepository;
import com.wms.repository.PieceWorkRepository;
import com.wms.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 日志报告定时通知服务
 * 支持邮件和企业微信机器人两种通知方式
 * 整合用户操作日志、系统日志进行格式化发送
 */
@Service
public class LogReportNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LogReportNotificationService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PieceWorkRepository pieceWorkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeChatWorkService weChatWorkService;

    @Autowired
    private WeChatOfficialService weChatOfficialService;

    @Autowired
    private WeChatMiniService weChatMiniService;

    // 动态邮件发送器（支持运行时配置）
    private JavaMailSenderImpl dynamicMailSender;

    @Value("${notification.enabled:false}")
    private boolean notificationEnabled;

    @Value("${notification.email.to:}")
    private String emailTo;

    @Value("${notification.email.from:}")
    private String emailFrom;

    @Value("${notification.wechat.webhook:}")
    private String wechatWebhook;

    @Value("${notification.wechat.work.user-ids:}")
    private String wechatWorkUserIds;

    @Value("${notification.wechat.official.open-ids:}")
    private String wechatOfficialOpenIds;

    @Value("${notification.wechat.mini.open-ids:}")
    private String wechatMiniOpenIds;

    @Value("${logging.file.name:logs/wms-application.log}")
    private String logFilePath;

    @Value("${notification.config.file:/opt/app/wms/backend/notification-config.json}")
    private String notificationConfigFile;

    // 动态邮件配置
    private String mailHost = "smtp.qq.com";
    private int mailPort = 587;
    private String mailUsername = "";
    private String mailPassword = "";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void loadPersistedNotificationConfig() {
        try {
            Path path = Paths.get(notificationConfigFile);
            if (!Files.exists(path)) {
                return;
            }

            Map<String, Object> config = objectMapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {});
            applyConfigMap(config);
        } catch (Exception e) {
            log.warn("加载通知配置文件失败: {}", e.getMessage());
        }
    }

    private void persistNotificationConfig() {
        try {
            Path path = Paths.get(notificationConfigFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Object> config = buildConfigMap(true);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
        } catch (Exception e) {
            log.warn("持久化通知配置失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildConfigMap(boolean includePassword) {
        Map<String, Object> config = new HashMap<>();
        config.put("emailTo", emailTo);
        config.put("emailFrom", emailFrom);
        config.put("mailHost", mailHost);
        config.put("mailPort", mailPort);
        config.put("mailUsername", mailUsername);
        if (includePassword) {
            config.put("mailPassword", mailPassword);
        }
        config.put("wechatWebhook", wechatWebhook);
        config.put("wechatWorkUserIds", wechatWorkUserIds);
        config.put("wechatOfficialOpenIds", wechatOfficialOpenIds);
        config.put("wechatMiniOpenIds", wechatMiniOpenIds);
        config.put("enabled", notificationEnabled);
        config.put("updatedAt", LocalDateTime.now().toString());
        return config;
    }

    private void applyConfigMap(Map<String, Object> config) {
        if (config == null) return;

        if (config.containsKey("emailTo")) {
            this.emailTo = (String) config.get("emailTo");
        }
        if (config.containsKey("emailFrom")) {
            this.emailFrom = (String) config.get("emailFrom");
        }
        if (config.containsKey("wechatWebhook")) {
            this.wechatWebhook = (String) config.get("wechatWebhook");
        }
        if (config.containsKey("wechatWorkUserIds")) {
            this.wechatWorkUserIds = (String) config.get("wechatWorkUserIds");
        }
        if (config.containsKey("wechatOfficialOpenIds")) {
            this.wechatOfficialOpenIds = (String) config.get("wechatOfficialOpenIds");
        }
        if (config.containsKey("wechatMiniOpenIds")) {
            this.wechatMiniOpenIds = (String) config.get("wechatMiniOpenIds");
        }
        if (config.containsKey("enabled")) {
            this.notificationEnabled = Boolean.TRUE.equals(config.get("enabled"));
        }

        if (config.containsKey("mailHost")) {
            this.mailHost = (String) config.get("mailHost");
        }
        if (config.containsKey("mailPort")) {
            Object port = config.get("mailPort");
            if (port instanceof Number) {
                this.mailPort = ((Number) port).intValue();
            } else if (port instanceof String) {
                this.mailPort = Integer.parseInt((String) port);
            }
        }
        if (config.containsKey("mailUsername")) {
            this.mailUsername = (String) config.get("mailUsername");
        }
        if (config.containsKey("mailPassword")) {
            String pwd = (String) config.get("mailPassword");
            if (pwd != null && !pwd.isEmpty()) {
                this.mailPassword = pwd;
            }
        }

        this.dynamicMailSender = null;
    }

    /**
     * 每天21:40执行一次
     * cron: 秒 分 时 日 月 周
     * 0 40 21 * * * = 每天21:40
     */
    @Scheduled(cron = "0 40 21 * * *")
    public void sendScheduledLogReport() {
        if (!notificationEnabled) {
            log.info("日志通知功能未启用，跳过发送");
            return;
        }

        log.info("开始执行定时日志报告发送任务...");

        try {
            String reportContent = generateLogReport();
            
            // 发送邮件（检查消息类型设置）
            if (isEmailConfigured() && shouldSendMessage("daily_report", "email")) {
                sendEmailReport(reportContent, "daily_report");
            }

            // 发送企业微信机器人（检查消息类型设置）
            if (isWechatConfigured() && shouldSendMessage("daily_report", "wechat")) {
                sendWechatReport(reportContent, "daily_report");
            }

            // 发送到指定的企业微信用户（如果配置了）
            if (shouldSendMessage("daily_report", "wechat_work") && weChatWorkService.isEnabled() && wechatWorkUserIds != null && !wechatWorkUserIds.isEmpty()) {
                String[] userIds = wechatWorkUserIds.split(",");
                for (String userId : userIds) {
                    userId = userId.trim();
                    if (!userId.isEmpty()) {
                        sendWeChatWorkReport(reportContent, userId);
                    }
                }
            }

            // 发送到指定的微信公众号用户（如果配置了）
            if (shouldSendMessage("daily_report", "wechat_official") && weChatOfficialService.isEnabled() && wechatOfficialOpenIds != null && !wechatOfficialOpenIds.isEmpty()) {
                String[] openIds = wechatOfficialOpenIds.split(",");
                for (String openId : openIds) {
                    openId = openId.trim();
                    if (!openId.isEmpty()) {
                        sendWeChatOfficialReport(reportContent, openId, "");
                    }
                }
            }

            // 发送到指定的微信小程序用户（如果配置了）
            if (shouldSendMessage("daily_report", "wechat_mini") && weChatMiniService.isEnabled() && wechatMiniOpenIds != null && !wechatMiniOpenIds.isEmpty()) {
                String[] openIds = wechatMiniOpenIds.split(",");
                for (String openId : openIds) {
                    openId = openId.trim();
                    if (!openId.isEmpty()) {
                        sendWeChatMiniReport(reportContent, openId, "");
                    }
                }
            }

            log.info("日志报告发送完成");
        } catch (Exception e) {
            log.error("发送日志报告失败", e);
        }
    }

    /**
     * 每月最后一天18:30发送计件统计报告
     * cron: 秒 分 时 日 月 周
     * 0 30 18 L * * = 每月最后一天18:30
     */
    @Scheduled(cron = "0 30 18 L * *")
    public void sendMonthlyPieceworkReport() {
        log.info("开始执行月度计件统计报告发送任务...");

        try {
            String reportContent = generateMonthlyPieceworkReport();
            
            // 发送邮件（检查消息类型设置）
            if (isEmailConfigured() && shouldSendMessage("piecework_report", "email")) {
                sendPieceworkEmailReport(reportContent);
                log.info("月度计件统计报告邮件已发送");
            } else {
                log.warn("邮件未配置或消息类型未启用，无法发送月度计件报告");
            }

            // 发送企业微信机器人（检查消息类型设置）
            if (isWechatConfigured() && shouldSendMessage("piecework_report", "wechat")) {
                sendWechatReport(reportContent, "piecework_report");
                log.info("月度计件统计报告已发送至企业微信机器人");
            }

            // 发送到指定的企业微信用户（如果配置了）
            if (shouldSendMessage("piecework_report", "wechat_work") && weChatWorkService.isEnabled() && wechatWorkUserIds != null && !wechatWorkUserIds.isEmpty()) {
                String[] userIds = wechatWorkUserIds.split(",");
                for (String userId : userIds) {
                    userId = userId.trim();
                    if (!userId.isEmpty()) {
                        sendWeChatWorkReport(reportContent, userId);
                    }
                }
                log.info("月度计件统计报告已发送至企业微信用户");
            }

            // 发送到指定的微信公众号用户（如果配置了）
            if (shouldSendMessage("piecework_report", "wechat_official") && weChatOfficialService.isEnabled() && wechatOfficialOpenIds != null && !wechatOfficialOpenIds.isEmpty()) {
                String[] openIds = wechatOfficialOpenIds.split(",");
                for (String openId : openIds) {
                    openId = openId.trim();
                    if (!openId.isEmpty()) {
                        sendWeChatOfficialReport(reportContent, openId, "");
                    }
                }
                log.info("月度计件统计报告已发送至微信公众号用户");
            }

            // 发送到指定的微信小程序用户（如果配置了）
            if (shouldSendMessage("piecework_report", "wechat_mini") && weChatMiniService.isEnabled() && wechatMiniOpenIds != null && !wechatMiniOpenIds.isEmpty()) {
                String[] openIds = wechatMiniOpenIds.split(",");
                for (String openId : openIds) {
                    openId = openId.trim();
                    if (!openId.isEmpty()) {
                        sendWeChatMiniReport(reportContent, openId, "");
                    }
                }
                log.info("月度计件统计报告已发送至微信小程序用户");
            }

        } catch (Exception e) {
            log.error("发送月度计件统计报告失败", e);
        }
    }

    /**
     * 手动触发发送月度计件报告
     */
    public Map<String, Object> sendManualPieceworkReport() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());

        try {
            String reportContent = generateMonthlyPieceworkReport();
            
            if (isEmailConfigured() && shouldSendMessage("piecework_report", "email")) {
                sendPieceworkEmailReport(reportContent);
                result.put("emailSent", true);
            }
            if (isWechatConfigured() && shouldSendMessage("piecework_report", "wechat")) {
                sendWechatReport(reportContent, "piecework_report");
                result.put("wechatSent", true);
            }

            result.put("success", true);
            result.put("message", "月度计件统计报告已发送");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
            log.error("手动发送月度计件报告失败", e);
        }
        return result;
    }

    /**
     * 手动触发发送日志报告（可通过API调用）
     */
    public Map<String, Object> sendManualReport() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());

        if (!notificationEnabled) {
            result.put("success", false);
            result.put("message", "通知功能未启用，请配置 NOTIFICATION_ENABLED=true");
            return result;
        }

        try {
            String reportContent = generateLogReport();
            List<String> sent = new ArrayList<>();

            if (isEmailConfigured() && shouldSendMessage("daily_report", "email")) {
                sendEmailReport(reportContent, "daily_report");
                sent.add("email:" + emailTo);
            }

            if (isWechatConfigured() && shouldSendMessage("daily_report", "wechat")) {
                sendWechatReport(reportContent, "daily_report");
                sent.add("wechat");
            }

            if (sent.isEmpty()) {
                result.put("success", false);
                result.put("message", "未配置任何通知渠道或消息类型未启用");
            } else {
                result.put("success", true);
                result.put("message", "发送成功");
                result.put("channels", sent);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
            log.error("手动发送日志报告失败", e);
        }

        return result;
    }

    public Map<String, Object> sendReportToWeChatWork(String userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());

        try {
            String reportContent = generateLogReport();
            sendWeChatWorkReport(reportContent, userId);
            result.put("success", true);
            result.put("message", "企业微信应用消息发送成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
            log.error("发送企业微信应用消息失败", e);
        }

        return result;
    }

    public Map<String, Object> sendReportToWeChatOfficial(String openId, String url) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());

        try {
            String reportContent = generateLogReport();
            sendWeChatOfficialReport(reportContent, openId, url);
            result.put("success", true);
            result.put("message", "微信公众号模板消息发送成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
            log.error("发送微信公众号模板消息失败", e);
        }

        return result;
    }

    public Map<String, Object> sendReportToWeChatMini(String openId, String page) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());

        try {
            String reportContent = generateLogReport();
            sendWeChatMiniReport(reportContent, openId, page);
            result.put("success", true);
            result.put("message", "微信小程序订阅消息发送成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
            log.error("发送微信小程序订阅消息失败", e);
        }

        return result;
    }

    /**
     * 生成日志报告内容
     * 整合系统日志和用户操作日志（详细版）
     */
    private String generateLogReport() {
        StringBuilder report = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        report.append("══════════════════════════════════════════════════════════\n");
        report.append("                    WMS系统运营日报\n");
        report.append("══════════════════════════════════════════════════════════\n\n");
        report.append("报告生成时间: ").append(LocalDateTime.now().format(formatter)).append("\n");
        report.append("统计周期: 最近3天\n\n");

        // 1. 用户操作日志统计
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        List<AuditLog> auditLogs = auditLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc(threeDaysAgo);
        
        report.append("┌──────────────────────────────────────────────────────────┐\n");
        report.append("│                    【用户操作日志】                       │\n");
        report.append("└──────────────────────────────────────────────────────────┘\n\n");
        
        if (!auditLogs.isEmpty()) {
            // 总体统计
            report.append("▶ 统计概览\n");
            report.append("  总操作数: ").append(auditLogs.size()).append(" 条\n\n");
            
            // 按模块统计
            Map<String, Long> moduleStats = auditLogs.stream()
                .collect(Collectors.groupingBy(AuditLog::getModule, Collectors.counting()));
            report.append("▶ 模块操作分布\n");
            moduleStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> report.append("  ").append(formatModule(e.getKey()))
                    .append(": ").append(e.getValue()).append(" 次\n"));
            
            // 按用户统计
            Map<String, Long> userStats = auditLogs.stream()
                .collect(Collectors.groupingBy(AuditLog::getUsername, Collectors.counting()));
            report.append("\n▶ 用户活跃度\n");
            userStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> report.append("  ").append(e.getKey())
                    .append(": ").append(e.getValue()).append(" 次操作\n"));
            
            // 按操作类型统计
            Map<String, Long> actionStats = auditLogs.stream()
                .collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));
            report.append("\n▶ 操作类型统计\n");
            actionStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(e -> report.append("  ").append(e.getKey())
                    .append(": ").append(e.getValue()).append(" 次\n"));
            
            // 详细操作记录（最近30条）
            report.append("\n▶ 详细操作记录 (最近30条)\n");
            report.append("──────────────────────────────────────────────────────────\n");
            auditLogs.stream().limit(30).forEach(al -> {
                String time = al.getCreatedAt().format(formatter);
                report.append("\n[").append(time).append("]\n");
                report.append("  用户: ").append(al.getUsername()).append("\n");
                report.append("  模块: ").append(formatModule(al.getModule())).append("\n");
                report.append("  操作: ").append(al.getAction()).append("\n");
                if (al.getDetails() != null && !al.getDetails().isEmpty()) {
                    report.append("  详情: ").append(al.getDetails()).append("\n");
                }
                if (al.getIpAddress() != null && !al.getIpAddress().isEmpty()) {
                    report.append("  IP: ").append(al.getIpAddress()).append("\n");
                }
            });
            report.append("──────────────────────────────────────────────────────────\n");
        } else {
            report.append("暂无操作记录\n");
        }

        // 2. 系统日志统计
        report.append("\n┌──────────────────────────────────────────────────────────┐\n");
        report.append("│                    【系统运行日志】                       │\n");
        report.append("└──────────────────────────────────────────────────────────┘\n\n");
        
        Map<String, Integer> stats = analyzeLogFile();
        int errorCount = stats.getOrDefault("ERROR", 0);
        int warnCount = stats.getOrDefault("WARN", 0);
        int infoCount = stats.getOrDefault("INFO", 0);
        
        report.append("▶ 日志级别统计\n");
        report.append("  ERROR: ").append(errorCount);
        if (errorCount > 0) report.append(" ⚠ 需关注");
        report.append("\n");
        report.append("  WARN:  ").append(warnCount).append("\n");
        report.append("  INFO:  ").append(infoCount).append("\n");

        // 最近的错误日志（显示更多）
        List<String> recentErrors = getRecentErrors(20);
        if (!recentErrors.isEmpty()) {
            report.append("\n▶ 最近错误日志详情\n");
            report.append("──────────────────────────────────────────────────────────\n");
            for (String error : recentErrors) {
                report.append(error).append("\n");
            }
            report.append("──────────────────────────────────────────────────────────\n");
        } else {
            report.append("\n✓ 无错误日志，系统运行正常\n");
        }

        // 最近的警告日志
        List<String> recentWarns = getRecentWarnings(10);
        if (!recentWarns.isEmpty()) {
            report.append("\n▶ 最近警告日志\n");
            report.append("──────────────────────────────────────────────────────────\n");
            for (String warn : recentWarns) {
                report.append(warn).append("\n");
            }
            report.append("──────────────────────────────────────────────────────────\n");
        }

        // 3. 系统状态
        report.append("\n┌──────────────────────────────────────────────────────────┐\n");
        report.append("│                    【系统运行状态】                       │\n");
        report.append("└──────────────────────────────────────────────────────────┘\n\n");
        report.append("  服务状态: 运行中\n");
        report.append("  JVM内存: ").append(getMemoryUsage()).append("\n");
        report.append("  报告周期: 每3天自动发送\n");
        report.append("  下次发送: 凌晨2:00\n");

        report.append("\n══════════════════════════════════════════════════════════\n");
        report.append("              此报告由WMS系统自动生成并发送\n");
        report.append("══════════════════════════════════════════════════════════\n");

        return report.toString();
    }

    /**
     * 获取最近的警告日志
     */
    private List<String> getRecentWarnings(int limit) {
        List<String> warnings = new ArrayList<>();
        File logFile = new File(logFilePath);
        if (!logFile.exists()) {
            logFile = new File("/opt/app/wms/logs/app.log");
        }
        if (!logFile.exists()) return warnings;

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(" WARN ")) {
                    warnings.add(line);
                    if (warnings.size() > limit * 2) {
                        warnings.remove(0);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("读取日志文件失败: {}", e.getMessage());
        }
        
        int start = Math.max(0, warnings.size() - limit);
        return warnings.subList(start, warnings.size());
    }

    /**
     * 格式化模块名称
     */
    private String formatModule(String module) {
        if (module == null) return "未知";
        switch (module.toUpperCase()) {
            case "PIECEWORK": return "计件";
            case "INVENTORY": return "库存";
            case "USER": return "用户";
            case "AUTH": return "认证";
            case "RULE": return "规则";
            case "STORAGE": return "入库";
            default: return module;
        }
    }

    /**
     * 获取管理员用户名列表
     */
    private Set<String> getAdminUsernames() {
        return userRepository.findAll().stream()
            .filter(u -> "ADMIN".equals(u.getRole()))
            .map(User::getUsername)
            .collect(Collectors.toSet());
    }

    /**
     * 生成月度计件统计报告（文本版，用于企业微信）
     */
    private String generateMonthlyPieceworkReport() {
        StringBuilder report = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy年MM月");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = now.withHour(23).withMinute(59).withSecond(59);
        String monthStr = now.format(monthFormatter);

        // 获取管理员用户名列表
        Set<String> adminUsers = getAdminUsernames();

        // 查询当月所有计件记录，过滤掉管理员的记录
        List<PieceWork> allRecords = pieceWorkRepository.findByWorkDateBetween(monthStart, monthEnd);
        List<PieceWork> validRecords = allRecords.stream()
            .filter(p -> !adminUsers.contains(p.getWorkerName()))
            .collect(Collectors.toList());

        report.append("══════════════════════════════════════════════════════════════\n");
        report.append("                    WMS月度计件统计报告\n");
        report.append("══════════════════════════════════════════════════════════════\n\n");
        report.append("统计月份: ").append(monthStr).append("\n");
        report.append("生成时间: ").append(now.format(formatter)).append("\n");
        report.append("统计范围: 普通用户（不含管理员）\n\n");

        // 总体统计
        int totalQuantity = validRecords.stream().mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0).sum();
        double totalAmount = validRecords.stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum();
        
        report.append("【总体统计】\n");
        report.append(String.format("  总记录数: %d 条\n", validRecords.size()));
        report.append(String.format("  总计件数: %,d 件\n", totalQuantity));
        report.append(String.format("  总金额:   ¥%,.2f\n\n", totalAmount));

        // 按工人统计
        report.append("【工人计件明细】\n");
        Map<String, List<PieceWork>> byWorker = validRecords.stream()
            .collect(Collectors.groupingBy(p -> p.getWorkerName() != null ? p.getWorkerName() : "未知"));
        
        byWorker.entrySet().stream()
            .sorted((a, b) -> Double.compare(
                b.getValue().stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum(),
                a.getValue().stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum()))
            .forEach(entry -> {
                int qty = entry.getValue().stream().mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0).sum();
                double amt = entry.getValue().stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum();
                report.append(String.format("  %s: %d件, ¥%.2f\n", entry.getKey(), qty, amt));
            });

        report.append("\n此报告由WMS系统自动生成，详细数据请查看邮件附件Excel表格\n");
        return report.toString();
    }

    /**
     * 生成月度计件统计Excel文件
     */
    private File generateMonthlyPieceworkExcel() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = now.withHour(23).withMinute(59).withSecond(59);
        String monthStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月"));

        // 获取管理员用户名列表
        Set<String> adminUsers = getAdminUsernames();

        // 查询当月所有计件记录，过滤掉管理员的记录
        List<PieceWork> allRecords = pieceWorkRepository.findByWorkDateBetween(monthStart, monthEnd);
        List<PieceWork> validRecords = allRecords.stream()
            .filter(p -> !adminUsers.contains(p.getWorkerName()))
            .collect(Collectors.toList());

        Workbook workbook = new XSSFWorkbook();
        
        // 创建样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Sheet1: 工人计件明细
        Sheet workerSheet = workbook.createSheet("工人计件明细");
        Row headerRow = workerSheet.createRow(0);
        String[] workerHeaders = {"工人姓名", "记录数", "总数量", "总金额"};
        for (int i = 0; i < workerHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(workerHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        Map<String, List<PieceWork>> byWorker = validRecords.stream()
            .collect(Collectors.groupingBy(p -> p.getWorkerName() != null ? p.getWorkerName() : "未知"));
        
        int rowNum = 1;
        for (Map.Entry<String, List<PieceWork>> entry : byWorker.entrySet().stream()
            .sorted((a, b) -> Double.compare(
                b.getValue().stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum(),
                a.getValue().stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum()))
            .collect(Collectors.toList())) {
            Row row = workerSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue().size());
            row.createCell(2).setCellValue(entry.getValue().stream().mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0).sum());
            row.createCell(3).setCellValue(entry.getValue().stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum());
        }
        for (int i = 0; i < workerHeaders.length; i++) workerSheet.autoSizeColumn(i);

        // Sheet2: 产品计件统计
        Sheet productSheet = workbook.createSheet("产品计件统计");
        Row productHeaderRow = productSheet.createRow(0);
        String[] productHeaders = {"产品名称", "总数量", "总金额"};
        for (int i = 0; i < productHeaders.length; i++) {
            Cell cell = productHeaderRow.createCell(i);
            cell.setCellValue(productHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        Map<String, List<PieceWork>> byProduct = validRecords.stream()
            .collect(Collectors.groupingBy(p -> p.getProductName() != null ? p.getProductName() : "未知"));
        
        rowNum = 1;
        for (Map.Entry<String, List<PieceWork>> entry : byProduct.entrySet().stream()
            .sorted((a, b) -> Integer.compare(
                b.getValue().stream().mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0).sum(),
                a.getValue().stream().mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0).sum()))
            .collect(Collectors.toList())) {
            Row row = productSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue().stream().mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0).sum());
            row.createCell(2).setCellValue(entry.getValue().stream().mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount().doubleValue() : 0).sum());
        }
        for (int i = 0; i < productHeaders.length; i++) productSheet.autoSizeColumn(i);

        // Sheet3: 详细记录
        Sheet detailSheet = workbook.createSheet("详细记录");
        Row detailHeaderRow = detailSheet.createRow(0);
        String[] detailHeaders = {"工人", "产品", "规格", "材质", "数量", "单价", "金额", "工作日期"};
        for (int i = 0; i < detailHeaders.length; i++) {
            Cell cell = detailHeaderRow.createCell(i);
            cell.setCellValue(detailHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        rowNum = 1;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (PieceWork pw : validRecords) {
            Row row = detailSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(pw.getWorkerName() != null ? pw.getWorkerName() : "");
            row.createCell(1).setCellValue(pw.getProductName() != null ? pw.getProductName() : "");
            row.createCell(2).setCellValue(pw.getSpecification() != null ? pw.getSpecification() : "");
            row.createCell(3).setCellValue(pw.getMaterial() != null ? pw.getMaterial() : "");
            row.createCell(4).setCellValue(pw.getQuantity() != null ? pw.getQuantity() : 0);
            row.createCell(5).setCellValue(pw.getUnitPrice() != null ? pw.getUnitPrice().doubleValue() : 0);
            row.createCell(6).setCellValue(pw.getTotalAmount() != null ? pw.getTotalAmount().doubleValue() : 0);
            row.createCell(7).setCellValue(pw.getWorkDate() != null ? pw.getWorkDate().format(dtf) : "");
        }

        // 保存文件
        File file = File.createTempFile("计件统计_" + monthStr + "_", ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
        workbook.close();
        
        return file;
    }

    /**
     * 截断字符串（处理中文）
     */
    private String truncateStr(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() > maxLen) {
            return str.substring(0, maxLen - 2) + "..";
        }
        return str;
    }

    /**
     * 发送计件统计邮件报告（带Excel附件）
     */
    private void sendPieceworkEmailReport(String content) throws MessagingException, IOException {
        JavaMailSender sender = getOrCreateMailSender();
        if (sender == null) {
            throw new MessagingException("邮件配置不完整，请检查SMTP设置");
        }

        // 生成Excel文件
        File excelFile = generateMonthlyPieceworkExcel();

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        LocalDateTime now = LocalDateTime.now();
        String monthStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月"));

        helper.setFrom(emailFrom);
        
        // 获取订阅了计件统计报告的接收者列表
        List<String> filteredRecipients = getFilteredEmailRecipients("piecework_report");
        if (filteredRecipients.isEmpty()) {
            log.info("没有接收者订阅计件统计报告");
            excelFile.delete(); // 删除临时文件
            return;
        }
        
        helper.setTo(filteredRecipients.toArray(new String[0]));
        helper.setSubject("【WMS系统】" + monthStr + "计件统计报告");
        helper.setText(content);
        
        // 添加Excel附件
        helper.addAttachment(monthStr + "计件统计.xlsx", excelFile);

        sender.send(message);
        log.info("月度计件统计邮件（含Excel附件）已发送至: {}", String.join(",", filteredRecipients));
        
        // 删除临时文件
        excelFile.delete();
    }

    /**
     * 分析日志文件统计
     */
    private Map<String, Integer> analyzeLogFile() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("ERROR", 0);
        stats.put("WARN", 0);
        stats.put("INFO", 0);

        File logFile = new File(logFilePath);
        if (!logFile.exists()) {
            // 尝试服务器路径
            logFile = new File("/opt/app/wms/logs/app.log");
        }

        if (!logFile.exists()) {
            return stats;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
            
            while ((line = reader.readLine()) != null) {
                // 简单统计最近3天的日志级别
                if (line.contains(" ERROR ")) {
                    stats.merge("ERROR", 1, Integer::sum);
                } else if (line.contains(" WARN ")) {
                    stats.merge("WARN", 1, Integer::sum);
                } else if (line.contains(" INFO ")) {
                    stats.merge("INFO", 1, Integer::sum);
                }
            }
        } catch (IOException e) {
            log.warn("读取日志文件失败: {}", e.getMessage());
        }

        return stats;
    }

    /**
     * 获取最近的错误日志
     */
    private List<String> getRecentErrors(int maxLines) {
        List<String> errors = new ArrayList<>();
        
        File logFile = new File(logFilePath);
        if (!logFile.exists()) {
            logFile = new File("/opt/app/wms/logs/app.log");
        }

        if (!logFile.exists()) {
            return errors;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(" ERROR ") || line.contains("Exception")) {
                    errors.add(line.length() > 200 ? line.substring(0, 200) + "..." : line);
                }
            }
        } catch (IOException e) {
            log.warn("读取日志文件失败: {}", e.getMessage());
        }

        // 只返回最后N条
        if (errors.size() > maxLines) {
            return errors.subList(errors.size() - maxLines, errors.size());
        }
        return errors;
    }

    /**
     * 获取内存使用情况
     */
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMB = runtime.maxMemory() / 1024 / 1024;
        return String.format("%dMB / %dMB (%.1f%%)", usedMB, maxMB, (double) usedMB / maxMB * 100);
    }

    /**
     * 发送邮件报告（支持按接收者过滤消息类型）
     */
    private void sendEmailReport(String content, String messageType) throws MessagingException {
        JavaMailSender sender = getOrCreateMailSender();
        if (sender == null) {
            log.warn("邮件发送器未配置");
            throw new MessagingException("邮件配置不完整，请检查SMTP设置");
        }

        // 获取邮件接收者列表和他们的消息类型设置
        List<String> filteredRecipients = getFilteredEmailRecipients(messageType);
        
        if (filteredRecipients.isEmpty()) {
            log.info("没有接收者订阅消息类型: {}", messageType);
            return;
        }

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(emailFrom);
        helper.setTo(filteredRecipients.toArray(new String[0]));
        
        // 根据消息类型设置不同的邮件主题
        String subject = getEmailSubject(messageType);
        helper.setSubject(subject);
        helper.setText(content);

        sender.send(message);
        log.info("邮件报告已发送至: {} (类型: {}, 接收者数量: {})", 
                 String.join(",", filteredRecipients), messageType, filteredRecipients.size());
    }

    /**
     * 获取订阅了指定消息类型的邮件接收者列表
     */
    private List<String> getFilteredEmailRecipients(String messageType) {
        List<String> filteredRecipients = new ArrayList<>();
        
        try {
            Map<String, Object> config = loadNotificationConfig();
            List<Map<String, Object>> recipients = (List<Map<String, Object>>) config.get("emailRecipients");
            
            if (recipients != null) {
                for (Map<String, Object> recipient : recipients) {
                    String email = (String) recipient.get("email");
                    Map<String, Object> messageTypes = (Map<String, Object>) recipient.get("messageTypes");
                    
                    if (email != null && messageTypes != null) {
                        Object enabled = messageTypes.get(messageType);
                        if (enabled instanceof Boolean && (Boolean) enabled) {
                            filteredRecipients.add(email);
                        }
                    }
                }
            }
            
            // 如果没有找到接收者配置，回退到使用emailTo字段
            if (filteredRecipients.isEmpty() && emailTo != null && !emailTo.isEmpty()) {
                String[] emails = emailTo.split(",");
                for (String email : emails) {
                    String trimmedEmail = email.trim();
                    if (!trimmedEmail.isEmpty()) {
                        filteredRecipients.add(trimmedEmail);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("获取邮件接收者列表失败，使用默认配置: {}", e.getMessage());
            
            // 出错时回退到使用emailTo字段
            if (emailTo != null && !emailTo.isEmpty()) {
                String[] emails = emailTo.split(",");
                for (String email : emails) {
                    String trimmedEmail = email.trim();
                    if (!trimmedEmail.isEmpty()) {
                        filteredRecipients.add(trimmedEmail);
                    }
                }
            }
        }
        
        return filteredRecipients;
    }

    /**
     * 根据消息类型获取邮件主题
     */
    private String getEmailSubject(String messageType) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        switch (messageType) {
            case "system_alert":
                return "【WMS系统】系统告警 - " + date;
            case "daily_report":
                return "【WMS系统】日志报告 - " + date;
            case "inventory_alert":
                return "【WMS系统】库存提醒 - " + date;
            case "piecework_report":
                return "【WMS系统】计件统计报告 - " + date;
            case "user_operation":
                return "【WMS系统】用户操作通知 - " + date;
            default:
                return "【WMS系统】通知 - " + date;
        }
    }

    /**
     * 获取或创建邮件发送器
     */
    private JavaMailSender getOrCreateMailSender() {
        if (mailUsername == null || mailUsername.isEmpty() || mailPassword == null || mailPassword.isEmpty()) {
            return null;
        }
        
        if (dynamicMailSender == null) {
            dynamicMailSender = new JavaMailSenderImpl();
        }
        
        dynamicMailSender.setHost(mailHost);
        dynamicMailSender.setPort(mailPort);
        dynamicMailSender.setUsername(mailUsername);
        dynamicMailSender.setPassword(mailPassword);
        
        Properties props = dynamicMailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        
        return dynamicMailSender;
    }

    /**
     * 发送企业微信机器人消息
     */
    private void sendWechatReport(String content, String messageType) {
        try {
            // 企业微信机器人消息格式
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            
            Map<String, Object> text = new HashMap<>();
            // 微信限制消息长度，截取关键部分
            String truncated = content.length() > 2000 ? content.substring(0, 2000) + "\n...(内容已截断)" : content;
            text.put("content", truncated);
            body.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(wechatWebhook, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("企业微信报告已发送 (类型: {})", messageType);
            } else {
                log.warn("企业微信发送失败: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("发送企业微信消息失败", e);
        }
    }

    private void sendWeChatWorkReport(String content, String userId) {
        if (!weChatWorkService.isEnabled()) {
            log.warn("企业微信应用消息服务未启用或配置不完整");
            return;
        }

        boolean success = weChatWorkService.sendTextMessage(userId, content);
        if (success) {
            log.info("企业微信应用消息发送成功, 用户: {}", userId);
        } else {
            log.error("企业微信应用消息发送失败, 用户: {}", userId);
        }
    }

    private void sendWeChatOfficialReport(String content, String openId, String url) {
        if (!weChatOfficialService.isEnabled()) {
            log.warn("微信公众号模板消息服务未启用或配置不完整");
            return;
        }

        String title = "WMS系统日志报告";
        boolean success = weChatOfficialService.sendTemplateMessage(openId, title, content, url);
        if (success) {
            log.info("微信公众号模板消息发送成功, openId: {}", openId);
        } else {
            log.error("微信公众号模板消息发送失败, openId: {}", openId);
        }
    }

    private void sendWeChatMiniReport(String content, String openId, String page) {
        if (!weChatMiniService.isEnabled()) {
            log.warn("微信小程序订阅消息服务未启用或配置不完整");
            return;
        }

        String title = "WMS系统日志报告";
        boolean success = weChatMiniService.sendSubscribeMessage(openId, title, content, page);
        if (success) {
            log.info("微信小程序订阅消息发送成功, openId: {}", openId);
        } else {
            log.error("微信小程序订阅消息发送失败, openId: {}", openId);
        }
    }

    private boolean isEmailConfigured() {
        return emailTo != null && !emailTo.isEmpty() 
            && emailFrom != null && !emailFrom.isEmpty()
            && mailUsername != null && !mailUsername.isEmpty()
            && mailPassword != null && !mailPassword.isEmpty();
    }

    private boolean isWechatConfigured() {
        return wechatWebhook != null && !wechatWebhook.isEmpty();
    }

    /**
     * 更新通知配置（运行时更新，重启后需重新配置）
     */
    public Map<String, Object> updateConfig(Map<String, Object> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (config.containsKey("mailPassword")) {
                Object pwd = config.get("mailPassword");
                if (pwd instanceof String && !((String) pwd).isEmpty()) {
                    this.mailPassword = (String) pwd;
                }
            }

            applyConfigMap(config);
            
            // 重置邮件发送器以应用新配置
            this.dynamicMailSender = null;

            persistNotificationConfig();

            result.put("success", true);
            result.put("message", "配置已更新，运行时生效");
            log.info("通知配置已更新: emailTo={}, mailHost={}, mailUsername={}, wechatConfigured={}", 
                     emailTo, mailHost, mailUsername, isWechatConfigured());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "配置更新失败: " + e.getMessage());
            log.error("更新通知配置失败", e);
        }
        return result;
    }

    /**
     * 获取通知配置状态
     */
    public Map<String, Object> getNotificationStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", notificationEnabled);
        status.put("emailConfigured", isEmailConfigured());
        status.put("emailTo", emailTo);
        status.put("wechatConfigured", isWechatConfigured());
        status.put("intervalDays", 1);
        status.put("nextSchedule", "每天21:40");
        return status;
    }

    /**
     * 获取完整通知配置（用于前端显示）
     */
    public Map<String, Object> getNotificationConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("emailTo", emailTo);
        config.put("emailFrom", emailFrom);
        config.put("mailHost", mailHost);
        config.put("mailPort", mailPort);
        config.put("mailUsername", mailUsername);
        // 注意：密码不返回，前端应从本地存储获取
        config.put("mailPasswordConfigured", mailPassword != null && !mailPassword.isEmpty());
        config.put("wechatWebhook", wechatWebhook);
        config.put("wechatWorkUserIds", wechatWorkUserIds);
        config.put("wechatOfficialOpenIds", wechatOfficialOpenIds);
        config.put("wechatMiniOpenIds", wechatMiniOpenIds);
        config.put("enabled", notificationEnabled);
        return config;
    }

    /**
     * 获取邮件接收者列表
     */
    public Map<String, Object> getEmailRecipients() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 从配置文件中读取邮件接收者列表
            Path path = Paths.get(notificationConfigFile);
            if (Files.exists(path)) {
                String content = Files.readString(path);
                Map<String, Object> config = new ObjectMapper().readValue(content, Map.class);
                List<Map<String, Object>> recipients = (List<Map<String, Object>>) config.get("emailRecipients");
                
                // 确保每个接收者都有消息类型设置
                if (recipients != null) {
                    for (Map<String, Object> recipient : recipients) {
                        Map<String, Object> messageTypes = (Map<String, Object>) recipient.get("messageTypes");
                        if (messageTypes == null) {
                            messageTypes = new HashMap<>();
                            messageTypes.put("system_alert", true);
                            messageTypes.put("daily_report", true);
                            messageTypes.put("inventory_alert", true);
                            messageTypes.put("piecework_report", true);
                            messageTypes.put("user_operation", false);
                            recipient.put("messageTypes", messageTypes);
                        }
                    }
                }
                
                result.put("success", true);
                result.put("recipients", recipients != null ? recipients : new ArrayList<>());
            } else {
                // 如果配置文件不存在，返回基于emailTo的默认接收者
                List<Map<String, Object>> defaultRecipients = new ArrayList<>();
                if (emailTo != null && !emailTo.isEmpty()) {
                    String[] emails = emailTo.split(",");
                    for (int i = 0; i < emails.length; i++) {
                        String email = emails[i].trim();
                        if (!email.isEmpty()) {
                            Map<String, Object> recipient = new HashMap<>();
                            recipient.put("email", email);
                            recipient.put("name", "接收者" + (i + 1));
                            recipient.put("role", "admin");
                            
                            // 添加默认消息类型设置
                            Map<String, Object> messageTypes = new HashMap<>();
                            messageTypes.put("system_alert", true);
                            messageTypes.put("daily_report", true);
                            messageTypes.put("inventory_alert", true);
                            messageTypes.put("piecework_report", true);
                            messageTypes.put("user_operation", false);
                            recipient.put("messageTypes", messageTypes);
                            
                            defaultRecipients.add(recipient);
                        }
                    }
                }
                result.put("success", true);
                result.put("recipients", defaultRecipients);
            }
        } catch (Exception e) {
            log.error("获取邮件接收者列表失败", e);
            result.put("success", false);
            result.put("message", "获取邮件接收者列表失败: " + e.getMessage());
            result.put("recipients", new ArrayList<>());
        }
        
        return result;
    }

    /**
     * 更新邮件接收者列表
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateEmailRecipients(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<Map<String, Object>> recipients = (List<Map<String, Object>>) request.get("recipients");
            if (recipients == null) {
                recipients = new ArrayList<>();
            }
            
            // 验证邮件接收者数据
            for (Map<String, Object> recipient : recipients) {
                String email = (String) recipient.get("email");
                String name = (String) recipient.get("name");
                
                if (email == null || email.trim().isEmpty()) {
                    result.put("success", false);
                    result.put("message", "邮箱地址不能为空");
                    return result;
                }
                
                if (name == null || name.trim().isEmpty()) {
                    result.put("success", false);
                    result.put("message", "接收者姓名不能为空");
                    return result;
                }
                
                // 验证邮箱格式
                if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    result.put("success", false);
                    result.put("message", "邮箱格式不正确: " + email);
                    return result;
                }
                
                // 确保每个接收者都有消息类型设置
                Map<String, Object> messageTypes = (Map<String, Object>) recipient.get("messageTypes");
                if (messageTypes == null) {
                    messageTypes = new HashMap<>();
                    messageTypes.put("system_alert", true);
                    messageTypes.put("daily_report", true);
                    messageTypes.put("inventory_alert", true);
                    messageTypes.put("piecework_report", true);
                    messageTypes.put("user_operation", false);
                    recipient.put("messageTypes", messageTypes);
                }
            }
            
            // 更新配置文件
            Map<String, Object> config = loadNotificationConfig();
            config.put("emailRecipients", recipients);
            config.put("updatedAt", LocalDateTime.now().toString());
            
            // 同时更新emailTo字段（用逗号分隔的邮箱列表，保持向后兼容）
            StringBuilder emailToBuilder = new StringBuilder();
            for (int i = 0; i < recipients.size(); i++) {
                if (i > 0) emailToBuilder.append(",");
                emailToBuilder.append(((String) recipients.get(i).get("email")).trim());
            }
            this.emailTo = emailToBuilder.toString();
            config.put("emailTo", this.emailTo);
            
            saveNotificationConfig(config);
            
            result.put("success", true);
            result.put("message", "邮件接收者列表更新成功");
            result.put("recipients", recipients);
            
            log.info("邮件接收者列表已更新，共 {} 个接收者", recipients.size());
            
        } catch (Exception e) {
            log.error("更新邮件接收者列表失败", e);
            result.put("success", false);
            result.put("message", "更新邮件接收者列表失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 加载通知配置
     */
    private Map<String, Object> loadNotificationConfig() {
        try {
            Path path = Paths.get(notificationConfigFile);
            if (Files.exists(path)) {
                String content = Files.readString(path);
                return new ObjectMapper().readValue(content, Map.class);
            }
        } catch (Exception e) {
            log.warn("加载通知配置失败", e);
        }
        return new HashMap<>();
    }

    /**
     * 保存通知配置
     */
    private void saveNotificationConfig(Map<String, Object> config) throws IOException {
        Path path = Paths.get(notificationConfigFile);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        Files.writeString(path, json);
    }

    /**
     * 获取消息类型设置
     */
    public Map<String, Object> getMessageTypes() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> config = loadNotificationConfig();
            
            // 默认消息类型设置
            Map<String, Object> defaultEmailTypes = new HashMap<>();
            defaultEmailTypes.put("system_alert", true);
            defaultEmailTypes.put("daily_report", true);
            defaultEmailTypes.put("inventory_alert", true);
            defaultEmailTypes.put("piecework_report", true);
            defaultEmailTypes.put("user_operation", false);
            
            Map<String, Object> defaultWechatTypes = new HashMap<>();
            defaultWechatTypes.put("system_alert", true);
            defaultWechatTypes.put("daily_report", true);
            defaultWechatTypes.put("inventory_alert", true);
            defaultWechatTypes.put("piecework_report", true);
            defaultWechatTypes.put("user_operation", false);
            
            Map<String, Object> defaultWechatWorkTypes = new HashMap<>();
            defaultWechatWorkTypes.put("system_alert", true);
            defaultWechatWorkTypes.put("daily_report", true);
            defaultWechatWorkTypes.put("inventory_alert", true);
            defaultWechatWorkTypes.put("piecework_report", true);
            defaultWechatWorkTypes.put("user_operation", false);
            
            Map<String, Object> defaultWechatOfficialTypes = new HashMap<>();
            defaultWechatOfficialTypes.put("system_alert", true);
            defaultWechatOfficialTypes.put("daily_report", true);
            defaultWechatOfficialTypes.put("inventory_alert", true);
            defaultWechatOfficialTypes.put("piecework_report", true);
            defaultWechatOfficialTypes.put("user_operation", false);
            
            Map<String, Object> defaultWechatMiniTypes = new HashMap<>();
            defaultWechatMiniTypes.put("system_alert", true);
            defaultWechatMiniTypes.put("daily_report", true);
            defaultWechatMiniTypes.put("inventory_alert", true);
            defaultWechatMiniTypes.put("piecework_report", true);
            defaultWechatMiniTypes.put("user_operation", false);
            
            Map<String, Object> emailTypes = (Map<String, Object>) config.getOrDefault("emailMessageTypes", defaultEmailTypes);
            Map<String, Object> wechatTypes = (Map<String, Object>) config.getOrDefault("wechatMessageTypes", defaultWechatTypes);
            Map<String, Object> wechatWorkTypes = (Map<String, Object>) config.getOrDefault("wechatWorkMessageTypes", defaultWechatWorkTypes);
            Map<String, Object> wechatOfficialTypes = (Map<String, Object>) config.getOrDefault("wechatOfficialMessageTypes", defaultWechatOfficialTypes);
            Map<String, Object> wechatMiniTypes = (Map<String, Object>) config.getOrDefault("wechatMiniMessageTypes", defaultWechatMiniTypes);
            
            result.put("success", true);
            result.put("emailMessageTypes", emailTypes);
            result.put("wechatMessageTypes", wechatTypes);
            result.put("wechatWorkMessageTypes", wechatWorkTypes);
            result.put("wechatOfficialMessageTypes", wechatOfficialTypes);
            result.put("wechatMiniMessageTypes", wechatMiniTypes);
            
        } catch (Exception e) {
            log.error("获取消息类型设置失败", e);
            result.put("success", false);
            result.put("message", "获取消息类型设置失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 更新消息类型设置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateMessageTypes(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> emailTypes = (Map<String, Object>) request.get("emailMessageTypes");
            Map<String, Object> wechatTypes = (Map<String, Object>) request.get("wechatMessageTypes");
            Map<String, Object> wechatWorkTypes = (Map<String, Object>) request.get("wechatWorkMessageTypes");
            Map<String, Object> wechatOfficialTypes = (Map<String, Object>) request.get("wechatOfficialMessageTypes");
            Map<String, Object> wechatMiniTypes = (Map<String, Object>) request.get("wechatMiniMessageTypes");
            
            if (emailTypes == null || wechatTypes == null || wechatWorkTypes == null || wechatOfficialTypes == null || wechatMiniTypes == null) {
                result.put("success", false);
                result.put("message", "消息类型设置不能为空");
                return result;
            }
            
            // 验证消息类型
            String[] validTypes = {"system_alert", "daily_report", "inventory_alert", "piecework_report", "user_operation"};
            Map<String, Map<String, Object>> allTypesMap = new HashMap<>();
            allTypesMap.put("email", emailTypes);
            allTypesMap.put("wechat", wechatTypes);
            allTypesMap.put("wechatWork", wechatWorkTypes);
            allTypesMap.put("wechatOfficial", wechatOfficialTypes);
            allTypesMap.put("wechatMini", wechatMiniTypes);
            
            for (Map.Entry<String, Map<String, Object>> entry : allTypesMap.entrySet()) {
                Map<String, Object> types = entry.getValue();
                for (String type : validTypes) {
                    if (!types.containsKey(type)) {
                        result.put("success", false);
                        result.put("message", entry.getKey() + "消息类型设置不完整，缺少类型: " + type);
                        return result;
                    }
                }
            }
            
            // 更新配置文件
            Map<String, Object> config = loadNotificationConfig();
            config.put("emailMessageTypes", emailTypes);
            config.put("wechatMessageTypes", wechatTypes);
            config.put("wechatWorkMessageTypes", wechatWorkTypes);
            config.put("wechatOfficialMessageTypes", wechatOfficialTypes);
            config.put("wechatMiniMessageTypes", wechatMiniTypes);
            config.put("updatedAt", LocalDateTime.now().toString());
            
            saveNotificationConfig(config);
            
            result.put("success", true);
            result.put("message", "消息类型设置更新成功");
            result.put("emailMessageTypes", emailTypes);
            result.put("wechatMessageTypes", wechatTypes);
            result.put("wechatWorkMessageTypes", wechatWorkTypes);
            result.put("wechatOfficialMessageTypes", wechatOfficialTypes);
            result.put("wechatMiniMessageTypes", wechatMiniTypes);
            
            log.info("消息类型设置已更新");            
        } catch (Exception e) {
            log.error("更新消息类型设置失败", e);
            result.put("success", false);
            result.put("message", "更新消息类型设置失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 检查是否应该发送指定类型的消息
     */
    private boolean shouldSendMessage(String messageType, String channel) {
        try {
            Map<String, Object> config = loadNotificationConfig();
            Map<String, Object> messageTypes;
            
            if ("email".equals(channel)) {
                messageTypes = (Map<String, Object>) config.get("emailMessageTypes");
            } else if ("wechat".equals(channel)) {
                messageTypes = (Map<String, Object>) config.get("wechatMessageTypes");
            } else if ("wechat_work".equals(channel)) {
                messageTypes = (Map<String, Object>) config.get("wechatWorkMessageTypes");
            } else if ("wechat_official".equals(channel)) {
                messageTypes = (Map<String, Object>) config.get("wechatOfficialMessageTypes");
            } else if ("wechat_mini".equals(channel)) {
                messageTypes = (Map<String, Object>) config.get("wechatMiniMessageTypes");
            } else {
                return true; // 未知渠道默认发送
            }
            
            if (messageTypes == null) {
                return true; // 未配置时默认发送
            }
            
            Object enabled = messageTypes.get(messageType);
            return enabled instanceof Boolean ? (Boolean) enabled : true;
            
        } catch (Exception e) {
            log.warn("检查消息类型设置失败，默认发送: {}", e.getMessage());
            return true;
        }
    }
}
