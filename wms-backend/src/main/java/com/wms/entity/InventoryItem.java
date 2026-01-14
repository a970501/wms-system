package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String productName;
    private String specification;
    private Integer quantity = 0;
    private String material;
    private String connectionType;
    private String unit = "个";
    private BigDecimal unitPrice;
    private String remarks;
    private Integer status = 1;
    
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    
    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }
}
