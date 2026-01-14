package com.wms.dto;

import lombok.Data;

/**
 * 定时备份配置DTO
 */
@Data
public class BackupScheduleConfig {
    
    /**
     * 是否启用自动备份
     */
    private Boolean enabled = false;
    
    /**
     * 备份频率: daily(每天), weekly(每周), monthly(每月)
     */
    private String frequency = "daily";
    
    /**
     * 备份时间 (HH:mm格式)
     */
    private String time = "02:00";
    
    /**
     * 保留天数
     */
    private Integer retentionDays = 30;
    
    /**
     * 是否包含数据库
     */
    private Boolean includeDatabase = true;
    
    /**
     * 是否包含文件
     */
    private Boolean includeFiles = true;
    
    /**
     * 是否启用邮件通知
     */
    private Boolean emailNotification = false;
    
    /**
     * 通知邮箱
     */
    private String notificationEmail;
}
