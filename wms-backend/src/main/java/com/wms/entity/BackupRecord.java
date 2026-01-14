package com.wms.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 备份记录实体
 */
@Entity
@Table(name = "backup_records")
@Data
public class BackupRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 备份名称
     */
    @Column(nullable = false, length = 200)
    private String name;
    
    /**
     * 备份类型: full(完整), database(数据库), files(文件), incremental(增量)
     */
    @Column(nullable = false, length = 50)
    private String type;
    
    /**
     * 文件路径
     */
    @Column(length = 500)
    private String filePath;
    
    /**
     * 文件大小（字节）
     */
    private Long size;
    
    /**
     * 状态: pending(等待), running(进行中), completed(完成), failed(失败)
     */
    @Column(nullable = false, length = 50)
    private String status = "pending";
    
    /**
     * 描述
     */
    @Column(length = 500)
    private String description;
    
    /**
     * 错误信息（如果失败）
     */
    @Column(length = 1000)
    private String errorMessage;
    
    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    
    /**
     * 创建者
     */
    @Column(length = 100)
    private String createdBy;
    
    /**
     * 是否为自动备份
     */
    @Column(nullable = false)
    private Boolean isAutomatic = false;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
