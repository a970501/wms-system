package com.wms.scheduled;

import com.wms.service.BackupService;
import com.wms.dto.BackupScheduleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时备份任务
 */
@Component
public class BackupScheduledTask {
    
    private static final Logger logger = LoggerFactory.getLogger(BackupScheduledTask.class);
    
    @Autowired
    private BackupService backupService;
    
    /**
     * 每天凌晨2点执行自动备份
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void executeScheduledBackup() {
        try {
            logger.info("开始执行定时备份任务");
            
            // 获取定时备份配置
            BackupScheduleConfig config = backupService.getScheduleConfig();
            
            // 检查是否启用
            if (!config.getEnabled()) {
                logger.info("自动备份未启用，跳过");
                return;
            }
            
            // 执行备份
            backupService.createBackup(
                config.getIncludeDatabase(),
                config.getIncludeFiles(),
                "自动备份 - " + java.time.LocalDateTime.now()
            );
            
            logger.info("定时备份任务完成");
            
            // 清理过期备份
            if (config.getRetentionDays() != null && config.getRetentionDays() > 0) {
                int cleaned = backupService.cleanupOldBackups(config.getRetentionDays());
                logger.info("清理了 {} 个过期备份", cleaned);
            }
            
        } catch (Exception e) {
            logger.error("定时备份任务执行失败", e);
        }
    }
    
    /**
     * 每周日凌晨3点执行完整备份
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void executeWeeklyFullBackup() {
        try {
            logger.info("开始执行每周完整备份");
            
            backupService.createBackup(
                true, // 包含数据库
                true, // 包含文件
                "每周完整备份 - " + java.time.LocalDateTime.now()
            );
            
            logger.info("每周完整备份任务完成");
            
        } catch (Exception e) {
            logger.error("每周完整备份失败", e);
        }
    }
    
    /**
     * 每天凌晨4点清理超过30天的备份
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupOldBackups() {
        try {
            logger.info("开始清理过期备份");
            
            int cleaned = backupService.cleanupOldBackups(30);
            
            logger.info("清理过期备份完成，共清理 {} 个", cleaned);
            
        } catch (Exception e) {
            logger.error("清理过期备份失败", e);
        }
    }
}
