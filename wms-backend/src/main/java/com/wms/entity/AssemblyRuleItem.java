package com.wms.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Entity
@Table(name = "assembly_rule_items")
public class AssemblyRuleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    @JsonIgnore
    private AssemblyRule assemblyRule;
    
    @Column(name = "component_name", nullable = false, length = 100)
    private String componentName;
    
    @Column(name = "quantity")
    private Integer quantity = 1;
    
    @Column(name = "is_required")
    private Boolean isRequired = true;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
