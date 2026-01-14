package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inventory_logs")
public class InventoryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "piecework_id", nullable = false)
    private Long pieceworkId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "inventory_type", nullable = false, length = 20)
    private String inventoryType;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "specification", length = 100)
    private String specification;

    @Column(name = "material", length = 100)
    private String material;

    @Column(name = "original_quantity", nullable = false)
    private Integer originalQuantity;

    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    @Column(name = "calculation_factor")
    private Double calculationFactor = 1.0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

