package com.wms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssemblyCheckResult {
    private Boolean canAssemble;
    private List<PartStatus> parts = new ArrayList<>();
    private List<String> insufficientParts = new ArrayList<>();

    @Data
    public static class PartStatus {
        private String componentName;
        private Integer required;
        private Integer available;
        private Boolean sufficient;
    }
}
