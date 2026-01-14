package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.AssemblyCheckRequest;
import com.wms.dto.AssemblyCheckResult;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

@Service
public class AssemblyService {

    @Autowired
    private AssemblyRecordRepository recordRepository;

    @Autowired
    private AssemblyRuleRepository ruleRepository;

    @Autowired
    private InventoryItemRepository inventoryRepository;
    
    @Autowired
    private FinishedProductService finishedProductService;
    
    @Autowired
    private AssemblyDefectRepository defectRepository;

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean equalsOrBlankEquals(String a, String b) {
        if (isBlank(a) && isBlank(b)) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private boolean nameMatches(String invName, String partName) {
        if (isBlank(invName) || isBlank(partName)) return false;
        return invName.equals(partName);
    }

    private boolean isZhongTouGaiPart(String partName) {
        return !isBlank(partName) && partName.contains("中头盖");
    }

    private boolean connectionTypeMatches(String invCt, String reqCt, String partName) {
        // 需求没填连接类型：不约束
        if (isBlank(reqCt)) return true;
        // 仅“中头盖”类零件允许库存连接类型为空（兼容历史库存不填螺纹）
        if (isBlank(invCt)) return isZhongTouGaiPart(partName);
        return invCt.equals(reqCt);
    }

    private InventoryItem findMatchingInventoryItem(String componentName, String specification, String material, String connectionType) {
        List<InventoryItem> candidates = inventoryRepository.findAll();
        InventoryItem best = null;
        int bestScore = -1;

        for (InventoryItem inv : candidates) {
            if (inv == null) continue;
            if (!nameMatches(inv.getProductName(), componentName)) continue;
            if (!equalsOrBlankEquals(inv.getSpecification(), specification)) continue;
            if (!equalsOrBlankEquals(inv.getMaterial(), material)) continue;
            if (!connectionTypeMatches(inv.getConnectionType(), connectionType, componentName)) continue;

            int score = 0;
            if (inv.getProductName() != null && inv.getProductName().equals(componentName)) score += 10;
            if (equalsOrBlankEquals(inv.getConnectionType(), connectionType)) {
                score += 2;
            } else if (isBlank(inv.getConnectionType()) && isZhongTouGaiPart(componentName)) {
                // 中头盖允许库存 connectionType 为空匹配，但优先级略低于完全匹配
                score += 1;
            }
            int qty = inv.getQuantity() == null ? 0 : inv.getQuantity();
            score += Math.min(qty, 1000);

            if (score > bestScore) {
                bestScore = score;
                best = inv;
            }
        }

        return best;
    }

    /**
     * 执行装配：扣减零件、保存记录、记录废品、成品入库
     */
    @Transactional
    public AssemblyRecord executeAssembly(AssemblyRecord record) {
        System.out.println("=== 开始装配 ===");
        System.out.println("产品: " + record.getProductName());
        System.out.println("数量: " + record.getQuantity());
        System.out.println("规则ID: " + record.getAssemblyRuleId());
        
        // 验证组装规则ID不为空
        if (record.getAssemblyRuleId() == null) {
            throw new RuntimeException("组装规则ID不能为空");
        }
        
        // 验证组装规则存在
        AssemblyRule rule = ruleRepository.findById(record.getAssemblyRuleId()).orElse(null);
        if (rule == null || !rule.getIsEnabled()) {
            throw new RuntimeException("组装规则不存在或已禁用");
        }

        // 扣减零件库存
        if (rule.getItems() != null && !rule.getItems().isEmpty()) {
            System.out.println("=== 扣减零件库存 ===");
            for (AssemblyRuleItem item : rule.getItems()) {
                int requiredQty = item.getQuantity() * record.getQuantity();
                System.out.println("零件: " + item.getComponentName() + ", 需要: " + requiredQty);

                InventoryItem inventory = findMatchingInventoryItem(
                    item.getComponentName(),
                    record.getSpecification(),
                    record.getMaterial(),
                    record.getConnectionType()
                );

                if (inventory == null) {
                    throw new RuntimeException("零件库存不存在: " + item.getComponentName());
                }
                
                // 检查库存是否充足
                if (inventory.getQuantity() < requiredQty) {
                    throw new RuntimeException("零件库存不足: " + item.getComponentName() + 
                        " (需要: " + requiredQty + ", 库存: " + inventory.getQuantity() + ")");
                }
                
                // 扣减库存
                int newQty = inventory.getQuantity() - requiredQty;
                inventory.setQuantity(newQty);
                inventoryRepository.save(inventory);
                System.out.println("✓ 已扣减: " + item.getComponentName() + ", 剩余: " + newQty);
            }
        }

        // 保存装配记录
        record.setStatus("completed");
        AssemblyRecord savedRecord = recordRepository.save(record);
        System.out.println("✓ 装配记录已保存 ID: " + savedRecord.getId());
        
        // 保存废品记录
        if (record.getDefects() != null && !record.getDefects().isEmpty()) {
            System.out.println("=== 保存废品记录 ===");
            for (Map<String, Object> defectData : record.getDefects()) {
                AssemblyDefect defect = new AssemblyDefect();
                defect.setAssemblyRecordId(savedRecord.getId());
                defect.setProductName((String) defectData.get("productName"));
                defect.setSpecification((String) defectData.get("specification"));
                defect.setMaterial((String) defectData.get("material"));
                defect.setConnectionType((String) defectData.get("connectionType"));
                defect.setQuantity(((Number) defectData.get("quantity")).intValue());
                defect.setUnit((String) defectData.getOrDefault("unit", "个"));
                defect.setDefectReason((String) defectData.get("defectReason"));
                
                defectRepository.save(defect);
                System.out.println("✓ 已记录废品: " + defect.getProductName() + " x" + defect.getQuantity());
            }
        }
        
        // 自动入库到成品仓库
        try {
            finishedProductService.createOrUpdate(
                record.getProductName(),
                record.getSpecification(),
                record.getMaterial(),
                record.getConnectionType(),
                record.getQuantity(),
                savedRecord.getId()
            );
            System.out.println("✓ 成品已入库");
        } catch (Exception e) {
            System.err.println("⚠️ 成品入库失败: " + e.getMessage());
        }
        
        return savedRecord;
    }

    public AssemblyCheckResult checkAssembly(AssemblyCheckRequest request) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        if (request.getAssemblyRuleId() == null) {
            throw new BusinessException("组装规则ID不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessException("数量必须大于0");
        }

        AssemblyRule rule = ruleRepository.findById(request.getAssemblyRuleId()).orElse(null);
        if (rule == null) {
            throw new BusinessException("组装规则不存在");
        }
        if (!Boolean.TRUE.equals(rule.getIsEnabled())) {
            throw new BusinessException("组装规则已禁用");
        }

        AssemblyCheckResult result = new AssemblyCheckResult();
        result.setCanAssemble(true);
        result.setParts(new ArrayList<>());
        result.setInsufficientParts(new ArrayList<>());

        if (rule.getItems() == null || rule.getItems().isEmpty()) {
            return result;
        }

        for (AssemblyRuleItem item : rule.getItems()) {
            int requiredQty = (item.getQuantity() == null ? 1 : item.getQuantity()) * request.getQuantity();

            AssemblyCheckResult.PartStatus ps = new AssemblyCheckResult.PartStatus();
            ps.setComponentName(item.getComponentName());
            ps.setRequired(requiredQty);

            int available = 0;
            InventoryItem inventory = findMatchingInventoryItem(
                item.getComponentName(),
                request.getSpecification(),
                request.getMaterial(),
                request.getConnectionType()
            );
            if (inventory != null && inventory.getQuantity() != null) {
                available = inventory.getQuantity();
            }

            ps.setAvailable(available);
            boolean sufficient = available >= requiredQty;
            ps.setSufficient(sufficient);
            result.getParts().add(ps);

            if (!sufficient) {
                result.getInsufficientParts().add(item.getComponentName());
                result.setCanAssemble(false);
            }
        }

        return result;
    }

    public List<AssemblyRecord> findAll() {
        return recordRepository.findAll();
    }

    public AssemblyRecord findById(Long id) {
        return recordRepository.findById(id).orElse(null);
    }

    public void deleteById(Long id) {
        recordRepository.deleteById(id);
    }
}
