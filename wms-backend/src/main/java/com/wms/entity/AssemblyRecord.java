package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "assembly_records")
public class AssemblyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assembly_rule_id")
    private Long assemblyRuleId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "specification", length = 50)
    private String specification;

    @Column(name = "material", length = 50)
    private String material;

    @Column(name = "connection_type", length = 20)
    private String connectionType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "status", length = 20)
    private String status = "completed";

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 瞬态字段，用于接收前端传来的废品数据，不存数据库
    @Transient
    private List<Map<String, Object>> defects;

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
