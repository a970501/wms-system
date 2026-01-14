package com.wms.dto;

import lombok.Data;

@Data
public class AssemblyCheckRequest {
    private Long assemblyRuleId;
    private String specification;
    private String material;
    private String connectionType;
    private Integer quantity;
}
