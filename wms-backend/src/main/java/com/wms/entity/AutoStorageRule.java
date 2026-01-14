package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "auto_storage_rules")
public class AutoStorageRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "product_pattern", nullable = false, unique = true, length = 100)
    private String productPattern;

    @Column(name = "target_location", nullable = false, length = 100)
    private String targetLocation;

    /**
     * 入库比例（如 1:1、1:2 等）
     */
    @Column(name = "storage_ratio", length = 10)
    private String storageRatio = "1:1";

    @Column(name = "priority")
    private Integer priority = 200;

    @Column(name = "trigger_condition", length = 50)
    private String triggerCondition = "ON_CREATE";

    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== 新增：毛坯消耗相关字段 =====
    
    /**
     * 是否是成品（成品入库时会消耗毛坯）
     */
    @Column(name = "is_finished_product")
    private Boolean isFinishedProduct = false;
    
    /**
     * 对应的毛坯产品名称
     * 当 isFinishedProduct=true 时，成品入库会从此毛坯产品中扣除库存
     */
    @Column(name = "blank_product_name", length = 255)
    private String blankProductName;
    
    /**
     * 每个成品需要的毛坯数量
     * 例如：1个球阀需要3个球阀毛坯，则此值为3
     */
    @Column(name = "blank_quantity_per_unit")
    private Integer blankQuantityPerUnit = 1;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
