package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.entity.AutoStorageRule;
import com.wms.service.AutoStorageRuleService;
import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/auto-storage-rules")
@RequireAuth
public class AutoStorageRuleController {

    @Autowired
    private AutoStorageRuleService service;

    @GetMapping
    public Result<List<AutoStorageRule>> getAll() {
        return Result.success(service.findAll());
    }

    @GetMapping("/{id}")
    public Result<AutoStorageRule> getById(@PathVariable Long id) {
        return Result.success(service.findById(id));
    }

    @PostMapping
    public Result<AutoStorageRule> create(@RequestBody AutoStorageRule rule) {
        return Result.success(service.save(rule));
    }

    /**
     * Update rule and recalculate all affected inventory
     */
    @PutMapping("/{id}")
    public Result<AutoStorageRule> update(@PathVariable Long id, @RequestBody AutoStorageRule rule) {
        System.out.println("=== Update rule request ===");
        System.out.println("Rule ID: " + id);
        System.out.println("New rule data: " + rule.getRuleName());
        
        // Use the service to update and recalculate
        AutoStorageRule updated = service.updateAndRecalculate(id, rule);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<AutoStorageRule> toggleEnabled(@PathVariable Long id) {
        AutoStorageRule rule = service.findById(id);
        if (rule != null) {
            rule.setIsEnabled(!rule.getIsEnabled());
            service.save(rule);
        }
        return Result.success(rule);
    }

    @PostMapping("/reapply")
    public Result<java.util.Map<String, Object>> reapplyRules() {
        long startTime = System.currentTimeMillis();
        java.util.Map<String, Integer> result = service.reapplyAllRules();
        long duration = System.currentTimeMillis() - startTime;
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("processedCount", result.get("processed"));
        response.put("updatedCount", result.get("updated"));
        response.put("duration", duration);
        
        return Result.success(response);
    }
}
