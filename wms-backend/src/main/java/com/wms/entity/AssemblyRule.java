package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "assembly_rules")
public class AssemblyRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;
    
    @Column(name = "product_name", nullable = false, unique = true, length = 100)
    private String productName;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "material_constraint", length = 100)
    private String materialConstraint;
    
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", length = 50)
    private String createdBy;
    
    @OneToMany(mappedBy = "assemblyRule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AssemblyRuleItem> items;
    
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
