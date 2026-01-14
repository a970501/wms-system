package com.wms.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assembly_defects")
public class AssemblyDefect {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long assemblyRecordId;
    
    @Column(nullable = false, length = 100)
    private String productName;
    
    @Column(length = 50)
    private String specification;
    
    @Column(length = 50)
    private String material;
    
    @Column(length = 20)
    private String connectionType;
    
    @Column(nullable = false)
    private Integer quantity = 0;
    
    @Column(length = 10)
    private String unit = "个";
    
    @Column(length = 500)
    private String defectReason;
    
    @Column(length = 500)
    private String remarks;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @Column
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
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getAssemblyRecordId() { return assemblyRecordId; }
    public void setAssemblyRecordId(Long assemblyRecordId) { this.assemblyRecordId = assemblyRecordId; }
    
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    
    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }
    
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    
    public String getDefectReason() { return defectReason; }
    public void setDefectReason(String defectReason) { this.defectReason = defectReason; }
    
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
