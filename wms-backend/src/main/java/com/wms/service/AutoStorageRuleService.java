package com.wms.service;

import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class AutoStorageRuleService {

    @Autowired
    private AutoStorageRuleRepository ruleRepository;

    @Autowired
    private InventoryLogRepository inventoryLogRepository;

    @Autowired
    private InventoryItemRepository inventoryRepository;

    @Autowired
    private BlankInventoryRepository blankInventoryRepository;

    @Autowired
    private PieceWorkRepository pieceWorkRepository;

    /**
     * Update rule and recalculate all affected inventory
     */
    @Transactional
    public AutoStorageRule updateAndRecalculate(Long id, AutoStorageRule newRule) {
        System.out.println("=== Updating rule and recalculating inventory ===");
        System.out.println("Rule ID: " + id);

        // 1. Get all logs affected by this rule
        List<InventoryLog> logs = inventoryLogRepository.findByRuleId(id);
        System.out.println("Found " + logs.size() + " inventory logs to recalculate");

        // 2. Rollback all old inventory changes
        for (InventoryLog log : logs) {
            rollbackLog(log);
        }

        // 3. Delete old logs
        for (InventoryLog log : logs) {
            inventoryLogRepository.delete(log);
        }

        // 4. Get existing and update
        AutoStorageRule existingRule = ruleRepository.findById(id).orElseThrow();
        existingRule.setRuleName(newRule.getRuleName());
        existingRule.setProductPattern(newRule.getProductPattern());
        existingRule.setTargetLocation(newRule.getTargetLocation());
        existingRule.setStorageRatio(newRule.getStorageRatio());
        existingRule.setPriority(newRule.getPriority());
        existingRule.setIsEnabled(newRule.getIsEnabled());
        existingRule.setDescription(newRule.getDescription());
        existingRule.setIsFinishedProduct(newRule.getIsFinishedProduct());
        existingRule.setBlankProductName(newRule.getBlankProductName());
        existingRule.setBlankQuantityPerUnit(newRule.getBlankQuantityPerUnit());
        AutoStorageRule savedRule = ruleRepository.save(existingRule);

        // 5. Recalculate for each piecework that used this rule
        for (InventoryLog log : logs) {
            // Only process parts logs (avoid duplicate processing)
            if (!"parts".equals(log.getInventoryType())) continue;

            Long pieceworkId = log.getPieceworkId();
            PieceWork pw = pieceWorkRepository.findById(pieceworkId).orElse(null);
            if (pw == null) continue;

            // Recalculate with new rule
            recalculateForPiecework(pw, savedRule);
        }

        System.out.println("=== Recalculation complete ===");
        return savedRule;
    }

    private void rollbackLog(InventoryLog log) {
        if ("parts".equals(log.getInventoryType())) {
            List<InventoryItem> items = inventoryRepository.findAll();
            InventoryItem item = items.stream()
                .filter(i -> log.getProductName().equals(i.getProductName())
                    && matches(log.getSpecification(), i.getSpecification())
                    && matches(log.getMaterial(), i.getMaterial()))
                .findFirst().orElse(null);

            if (item != null) {
                int newQty = item.getQuantity() - log.getQuantityChange();
                if (newQty <= 0) {
                    inventoryRepository.delete(item);
                } else {
                    item.setQuantity(newQty);
                    inventoryRepository.save(item);
                }
                System.out.println("Rolled back parts: " + log.getProductName() + " -" + log.getQuantityChange());
            }
        } else if ("blank".equals(log.getInventoryType())) {
            Optional<BlankInventory> blankOpt = blankInventoryRepository
                .findByProductNameAndSpecificationAndMaterial(
                    log.getProductName(), log.getSpecification(), log.getMaterial());

            if (blankOpt.isPresent()) {
                BlankInventory blank = blankOpt.get();
                blank.setQuantity(blank.getQuantity() - log.getQuantityChange());
                blankInventoryRepository.save(blank);
                System.out.println("Rolled back blank: " + log.getProductName() + " -" + log.getQuantityChange());
            }
        }
    }

    private void recalculateForPiecework(PieceWork pw, AutoStorageRule rule) {
        System.out.println("Recalculating for piecework ID: " + pw.getId());

        int quantity = pw.getQuantity();
        int defectQty = pw.getDefectQuantity() != null ? pw.getDefectQuantity() : 0;

        // Calculate inventory quantity (handle "两头" products)
        int inventoryQuantity = quantity;
        double factor = 1.0;
        if (pw.getProductName() != null && pw.getProductName().contains("两头")) {
            inventoryQuantity = quantity / 2;
            factor = 0.5;
        }

        // Add to parts inventory
        addToInventory(rule.getTargetLocation(), pw, inventoryQuantity);
        saveLog(pw.getId(), rule.getId(), "parts", rule.getTargetLocation(),
            pw.getSpecification(), pw.getMaterial(), quantity, inventoryQuantity, factor);

        // Consume blank if applicable
        if (Boolean.TRUE.equals(rule.getIsFinishedProduct())
            && rule.getBlankProductName() != null
            && !rule.getBlankProductName().trim().isEmpty()) {
            int blankConsumed = (quantity + defectQty) * rule.getBlankQuantityPerUnit();
            consumeBlank(rule.getBlankProductName(), pw, blankConsumed);
            saveLog(pw.getId(), rule.getId(), "blank", rule.getBlankProductName(),
                pw.getSpecification(), pw.getMaterial(), quantity + defectQty, -blankConsumed, 1.0);
        }
    }

    private void addToInventory(String productName, PieceWork pw, int quantity) {
        List<InventoryItem> items = inventoryRepository.findAll();
        InventoryItem item = items.stream()
            .filter(i -> productName.equals(i.getProductName())
                && matches(pw.getSpecification(), i.getSpecification())
                && matches(pw.getMaterial(), i.getMaterial()))
            .findFirst().orElse(null);

        if (item != null) {
            item.setQuantity(item.getQuantity() + quantity);
            inventoryRepository.save(item);
        } else {
            InventoryItem newItem = new InventoryItem();
            newItem.setProductName(productName);
            newItem.setSpecification(pw.getSpecification());
            newItem.setMaterial(pw.getMaterial());
            newItem.setQuantity(quantity);
            newItem.setConnectionType(pw.getConnectionType());
            newItem.setUnit(pw.getUnit());
            inventoryRepository.save(newItem);
        }
    }

    private void consumeBlank(String blankProductName, PieceWork pw, int quantity) {
        Optional<BlankInventory> blankOpt = blankInventoryRepository
            .findByProductNameAndSpecificationAndMaterial(
                blankProductName, pw.getSpecification(), pw.getMaterial());

        BlankInventory blank;
        if (blankOpt.isPresent()) {
            blank = blankOpt.get();
        } else {
            blank = new BlankInventory();
            blank.setProductName(blankProductName);
            blank.setSpecification(pw.getSpecification());
            blank.setMaterial(pw.getMaterial());
            blank.setQuantity(0);
            blank.setUnit("个");
            blank = blankInventoryRepository.save(blank);
        }

        blank.setQuantity(blank.getQuantity() - quantity);
        blankInventoryRepository.save(blank);
    }

    private void saveLog(Long pieceworkId, Long ruleId, String type, String productName,
                         String spec, String material, int origQty, int changeQty, double factor) {
        InventoryLog log = new InventoryLog();
        log.setPieceworkId(pieceworkId);
        log.setRuleId(ruleId);
        log.setInventoryType(type);
        log.setProductName(productName);
        log.setSpecification(spec);
        log.setMaterial(material);
        log.setOriginalQuantity(origQty);
        log.setQuantityChange(changeQty);
        log.setCalculationFactor(factor);
        inventoryLogRepository.save(log);
    }

    private boolean matches(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equals(s2);
    }

    public AutoStorageRule save(AutoStorageRule rule) {
        return ruleRepository.save(rule);
    }

    public void deleteById(Long id) {
        ruleRepository.deleteById(id);
    }

    public List<AutoStorageRule> findAll() {
        return ruleRepository.findAll();
    }

    public AutoStorageRule findById(Long id) {
        return ruleRepository.findById(id).orElse(null);
    }

    /**
     * 重新应用所有入库规则到现有库存
     */
    public java.util.Map<String, Integer> reapplyAllRules() {
        int processedCount = 0;
        int updatedCount = 0;
        
        try {
            // 1. 获取所有启用的规则，按优先级排序
            java.util.List<AutoStorageRule> rules = ruleRepository.findAll().stream()
                .filter(r -> r.getIsEnabled() != null && r.getIsEnabled())
                .sorted((r1, r2) -> Integer.compare(
                    r2.getPriority() != null ? r2.getPriority() : 0,
                    r1.getPriority() != null ? r1.getPriority() : 0
                ))
                .collect(java.util.stream.Collectors.toList());
            
            if (rules.isEmpty()) {
                java.util.Map<String, Integer> result = new java.util.HashMap<>();
                result.put("processed", 0);
                result.put("updated", 0);
                return result;
            }
            
            // 2. 获取所有库存记录
            java.util.List<com.wms.entity.InventoryItem> allItems = inventoryRepository.findAll();
            processedCount = allItems.size();
            
            // 3. 遍历每条库存，尝试匹配规则
            for (com.wms.entity.InventoryItem item : allItems) {
                String originalName = item.getProductName();
                String newName = applyRulesToItem(item.getProductName(), rules);
                
                // 如果名称发生变化，更新记录
                if (newName != null && !newName.equals(originalName)) {
                    item.setProductName(newName);
                    inventoryRepository.save(item);
                    updatedCount++;
                }
            }
            
        } catch (Exception e) {
            System.err.println("重新应用规则失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        java.util.Map<String, Integer> result = new java.util.HashMap<>();
        result.put("processed", processedCount);
        result.put("updated", updatedCount);
        return result;
    }
    
    /**
     * 对单个产品名称应用规则
     */
    private String applyRulesToItem(String productName, java.util.List<AutoStorageRule> rules) {
        if (productName == null || productName.isEmpty()) {
            return null;
        }
        
        // 遍历规则，找到第一个匹配的
        for (AutoStorageRule rule : rules) {
            if (matchesPattern(productName, rule.getProductPattern())) {
                return rule.getTargetLocation();
            }
        }
        
        return null;
    }
    
    /**
     * 模式匹配（支持%通配符）
     */
    private boolean matchesPattern(String text, String pattern) {
        if (pattern == null || text == null) {
            return false;
        }
        
        String regex = pattern
            .replace(".", "\\\\.")
            .replace("*", "\\\\*")
            .replace("%", ".*");
        
        return text.matches(regex);
    }
}
