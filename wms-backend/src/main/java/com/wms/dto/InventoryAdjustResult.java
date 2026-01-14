package com.wms.dto;

import lombok.Data;

@Data
public class InventoryAdjustResult {
    private Long id;
    private Integer oldQuantity;
    private Integer newQuantity;
}
