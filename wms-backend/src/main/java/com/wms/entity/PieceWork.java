package com.wms.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "piece_works")
public class PieceWork {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_name")
    private String workerName;

    @Column(name = "product_name")
    private String productName;

    private String specification;
    private Integer quantity;
    private String material;

    @Column(name = "connection_type")
    private String connectionType;

    @Column(name = "semi_finished")
    private String semiFinished;

    private String unit;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    private String remarks;

    @Column(name = "defect_quantity")
    private Integer defectQuantity;

    @Column(name = "defective_reason")
    private String defectiveReason;

    @Column(name = "is_defective")
    private String isDefective;

    @Column(name = "should_deduct_blank")
    private Boolean shouldDeductBlank;

    @Column(name = "should_not_add_to_inventory")
    private Boolean shouldNotAddToInventory;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "work_date")
    private LocalDateTime workDate;

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
        if (workDate == null) workDate = LocalDateTime.now();
        calculateTotalAmount();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
        calculateTotalAmount();
    }

    private void calculateTotalAmount() {
        // 金额 = 正常数量 × 单价
        // 报废数量(defectQuantity)只扣毛坯库存，不计入金额
        // isDefective 只是标记"是否有报废信息"，不影响正常数量的金额计算
        if (quantity != null && unitPrice != null) {
            totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        } else {
            totalAmount = BigDecimal.ZERO;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }
    public String getSemiFinished() { return semiFinished; }
    public void setSemiFinished(String semiFinished) { this.semiFinished = semiFinished; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Integer getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(Integer defectQuantity) { this.defectQuantity = defectQuantity; }
    public String getDefectiveReason() { return defectiveReason; }
    public void setDefectiveReason(String defectiveReason) { this.defectiveReason = defectiveReason; }
    public String getIsDefective() { return isDefective; }
    public void setIsDefective(String isDefective) { this.isDefective = isDefective; }
    public Boolean getShouldDeductBlank() { return shouldDeductBlank; }
    public void setShouldDeductBlank(Boolean shouldDeductBlank) { this.shouldDeductBlank = shouldDeductBlank; }
    public Boolean getShouldNotAddToInventory() { return shouldNotAddToInventory; }
    public void setShouldNotAddToInventory(Boolean shouldNotAddToInventory) { this.shouldNotAddToInventory = shouldNotAddToInventory; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getWorkDate() { return workDate; }
    public void setWorkDate(LocalDateTime workDate) { this.workDate = workDate; }
}

