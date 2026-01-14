package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.entity.AssemblyRule;
import com.wms.repository.AssemblyRuleRepository;
import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/assembly-rules")
@RequireAuth
public class AssemblyRuleController {

    @Autowired
    private AssemblyRuleRepository repository;

    @GetMapping
    public Result<List<Map<String, Object>>> getAll() {
        List<AssemblyRule> rules = repository.findAll();
        List<Map<String, Object>> result = rules.stream().map(rule -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rule.getId());
            map.put("ruleName", rule.getRuleName());
            map.put("productName", rule.getProductName());
            map.put("description", rule.getDescription());
            map.put("materialConstraint", rule.getMaterialConstraint());
            map.put("isEnabled", rule.getIsEnabled());
            map.put("createdAt", rule.getCreatedAt());

            // 手动处理items，避免循环引用
            if (rule.getItems() != null) {
                List<Map<String, Object>> items = rule.getItems().stream().map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getId());
                    itemMap.put("componentName", item.getComponentName());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("isRequired", item.getIsRequired());
                    itemMap.put("sortOrder", item.getSortOrder());
                    return itemMap;
                }).collect(Collectors.toList());
                map.put("items", items);
            }

            return map;
        }).collect(Collectors.toList());

        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<AssemblyRule> getById(@PathVariable Long id) {
        return Result.success(repository.findById(id).orElse(null));
    }

    @PostMapping
    public Result<AssemblyRule> create(@RequestBody AssemblyRule rule) {
        // 设置items的反向关联
        if (rule.getItems() != null) {
            rule.getItems().forEach(item -> item.setAssemblyRule(rule));
        }
        return Result.success(repository.save(rule));
    }

    @PutMapping("/{id}")
    public Result<AssemblyRule> update(@PathVariable Long id, @RequestBody AssemblyRule rule) {
        AssemblyRule existing = repository.findById(id).orElse(null);
        if (existing != null) {
            // 更新基本字段
            existing.setProductName(rule.getProductName());
            existing.setIsEnabled(rule.getIsEnabled());
            existing.setDescription(rule.getDescription());
            
            // 处理items：清空旧的，添加新的
            if (existing.getItems() != null) {
                existing.getItems().clear();
            } else {
                existing.setItems(new java.util.ArrayList<>());
            }
            
            if (rule.getItems() != null) {
                rule.getItems().forEach(item -> {
                    item.setId(null);  // 清除ID，让JPA视为新记录
                    item.setAssemblyRule(existing);
                    existing.getItems().add(item);
                });
            }
            
            return Result.success(repository.save(existing));
        }
        return Result.error(404, "规则不存在");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<AssemblyRule> toggleEnabled(@PathVariable Long id) {
        AssemblyRule rule = repository.findById(id).orElse(null);
        if (rule != null) {
            rule.setIsEnabled(!Boolean.TRUE.equals(rule.getIsEnabled()));
            repository.save(rule);
            return Result.success(rule);
        }
        return Result.error(404, "规则不存在");
    }
}

