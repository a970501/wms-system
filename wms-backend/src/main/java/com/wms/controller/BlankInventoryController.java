package com.wms.controller;

import com.wms.entity.BlankInventory;
import com.wms.repository.BlankInventoryRepository;
import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 毛坯库存Controller
 */
@RestController
@RequestMapping("/blank-inventory")
public class BlankInventoryController {
    
    @Autowired
    private BlankInventoryRepository repository;
    
    /**
     * 获取所有毛坯库存
     */
    @GetMapping
    public Result<List<BlankInventory>> getAll() {
        List<BlankInventory> list = repository.findAllByOrderByCreatedAtDesc();
        return Result.success(list);
    }
    
    /**
     * 根据ID获取
     */
    @GetMapping("/{id}")
    public Result<BlankInventory> getById(@PathVariable Long id) {
        BlankInventory item = repository.findById(id).orElse(null);
        if (item == null) {
            return Result.error("记录不存在");
        }
        return Result.success(item);
    }
    
    /**
     * 创建或更新毛坯库存
     */
    @PostMapping
    public Result<BlankInventory> createOrUpdate(@RequestBody BlankInventory item) {
        try {
            // 查找是否已存在
            var existing = repository.findByProductNameAndSpecificationAndMaterial(
                item.getProductName(),
                item.getSpecification(),
                item.getMaterial()
            );
            
            if (existing.isPresent()) {
                // 更新现有记录
                BlankInventory existingItem = existing.get();
                existingItem.setQuantity(existingItem.getQuantity() + item.getQuantity());
                existingItem.setWorkerName(item.getWorkerName());
                if (item.getRemarks() != null) {
                    existingItem.setRemarks(item.getRemarks());
                }
                BlankInventory saved = repository.save(existingItem);
                return Result.success(saved);
            } else {
                // 创建新记录
                BlankInventory saved = repository.save(item);
                return Result.success(saved);
            }
        } catch (Exception e) {
            return Result.error("保存失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新
     */
    @PutMapping("/{id}")
    public Result<BlankInventory> update(@PathVariable Long id, @RequestBody BlankInventory item) {
        try {
            BlankInventory existing = repository.findById(id).orElse(null);
            if (existing == null) {
                return Result.error("记录不存在");
            }
            
            existing.setProductName(item.getProductName());
            existing.setSpecification(item.getSpecification());
            existing.setMaterial(item.getMaterial());
            existing.setQuantity(item.getQuantity());
            existing.setUnit(item.getUnit());
            existing.setWorkerName(item.getWorkerName());
            existing.setRemarks(item.getRemarks());
            
            BlankInventory saved = repository.save(existing);
            return Result.success(saved);
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            repository.deleteById(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
