package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.annotation.Auditable;
import com.wms.common.BusinessException;
import com.wms.entity.InventoryItem;
import com.wms.repository.InventoryItemRepository;
import com.wms.dto.InventoryAdjustRequest;
import com.wms.dto.InventoryAdjustResult;
import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
@RequireAuth
public class InventoryController {
    
    @Autowired
    private InventoryItemRepository repository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @GetMapping("/items")
    public Result<List<InventoryItem>> getAll() {
        return Result.success(repository.findAll());
    }
    
    @GetMapping("/items/{id}")
    public Result<InventoryItem> getById(@PathVariable Long id) {
        return Result.success(repository.findById(id).orElse(null));
    }
    
    @GetMapping("/items/{id}/history")
    public Result<List<Map<String, Object>>> getHistory(@PathVariable Long id) {
        // 获取库存项信息
        InventoryItem item = repository.findById(id).orElse(null);
        if (item == null) {
            return Result.error("库存项不存在");
        }
        
        // 查询该产品的所有变动记录（简化查询，只匹配产品名和规格）
        StringBuilder sql = new StringBuilder(
            "SELECT il.*, pw.work_date, pw.worker_name " +
            "FROM inventory_logs il " +
            "LEFT JOIN piece_works pw ON il.piecework_id = pw.id " +
            "WHERE il.product_name = ? "
        );
        
        List<Object> params = new java.util.ArrayList<>();
        params.add(item.getProductName());
        
        // 处理规格匹配
        if (item.getSpecification() != null && !item.getSpecification().isEmpty()) {
            sql.append("AND il.specification = ? ");
            params.add(item.getSpecification());
        } else {
            sql.append("AND (il.specification IS NULL OR il.specification = '') ");
        }
        
        sql.append("ORDER BY il.created_at DESC LIMIT 50");
        
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
            sql.toString(), 
            params.toArray()
        );
        
        return Result.success(history);
    }
    
    @PostMapping("/items")
    public Result<InventoryItem> create(@RequestBody InventoryItem item) {
        return Result.success(repository.save(item));
    }
    
    @PutMapping("/items/{id}")
    public Result<InventoryItem> update(@PathVariable Long id, @RequestBody InventoryItem item) {
        // 获取原记录
        InventoryItem original = repository.findById(id).orElse(null);
        if (original == null) {
            return Result.error("库存项不存在");
        }
        
        // 记录库存变动
        int quantityChange = item.getQuantity() - original.getQuantity();
        if (quantityChange != 0) {
            String sql = "INSERT INTO inventory_logs " +
                         "(piecework_id, inventory_type, product_name, specification, material, connection_type, " +
                         "original_quantity, quantity_change, created_at) " +
                         "VALUES (0, 'parts', ?, ?, ?, ?, ?, ?, NOW())";
            
            jdbcTemplate.update(sql,
                item.getProductName(),
                item.getSpecification(),
                item.getMaterial(),
                item.getConnectionType(),
                original.getQuantity(),
                quantityChange
            );
        }
        
        item.setId(id);
        return Result.success(repository.save(item));
    }
    
    @PostMapping("/items/{id}/adjust")
    public Result<InventoryAdjustResult> adjust(@PathVariable Long id, @RequestBody InventoryAdjustRequest request) {
        if (request == null || request.getDelta() == null) {
            throw new BusinessException(400, "delta不能为空");
        }

        InventoryItem original = repository.findById(id).orElse(null);
        if (original == null) {
            return Result.error("库存项不存在");
        }

        int oldQty = original.getQuantity() == null ? 0 : original.getQuantity();
        int delta = request.getDelta();
        int newQty = oldQty + delta;
        if (newQty < 0) {
            throw new BusinessException(400, "库存不足，无法扣减");
        }

        if (delta != 0) {
            String sql = "INSERT INTO inventory_logs " +
                "(piecework_id, inventory_type, product_name, specification, material, connection_type, " +
                "original_quantity, quantity_change, created_at) " +
                "VALUES (0, 'parts', ?, ?, ?, ?, ?, ?, NOW())";

            jdbcTemplate.update(sql,
                original.getProductName(),
                original.getSpecification(),
                original.getMaterial(),
                original.getConnectionType(),
                oldQty,
                delta
            );
        }

        original.setQuantity(newQty);
        repository.save(original);

        InventoryAdjustResult out = new InventoryAdjustResult();
        out.setId(id);
        out.setOldQuantity(oldQty);
        out.setNewQuantity(newQty);
        return Result.success(out);
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return Result.success();
    }
    
    @GetMapping("/stats")
    public Result<InventoryStats> getStats() {
        List<InventoryItem> items = repository.findAll();
        InventoryStats stats = new InventoryStats();
        stats.setTotalItems(items.size());
        stats.setTotalQuantity(items.stream().mapToInt(InventoryItem::getQuantity).sum());
        return Result.success(stats);
    }
    
    static class InventoryStats {
        private Integer totalItems;
        private Integer totalQuantity;
        
        public Integer getTotalItems() { return totalItems; }
        public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }
        public Integer getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    }
}
