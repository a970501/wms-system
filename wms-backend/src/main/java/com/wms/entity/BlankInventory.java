package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 毛坯库存实体（独立于成品库存）
 */
@Data
@Entity
@Table(name = "blank_inventory")
public class BlankInventory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;
    
    @Column(name = "specification", length = 50)
    private String specification;
    
    @Column(name = "material", length = 50)
    private String material;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;
    
    @Column(name = "unit", length = 20)
    private String unit = "个";
    
    @Column(name = "worker_name", length = 100)
    private String workerName;
    
    @Column(name = "remarks", length = 500)
    private String remarks;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
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
