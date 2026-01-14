package com.wms.dto;

import lombok.Data;

@Data
public class InventoryAdjustRequest {
    private Integer delta;
    private String remark;
}
