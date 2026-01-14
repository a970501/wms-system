package com.wms.controller;

import com.wms.entity.PriceTable;
import com.wms.entity.PieceWork;
import com.wms.repository.PriceTableRepository;
import com.wms.repository.PieceWorkRepository;
import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/price-tables")
public class PriceTableController {

    @Autowired
    private PriceTableRepository repository;

    @Autowired
    private PieceWorkRepository pieceWorkRepository;

    @GetMapping
    public Result<List<PriceTable>> getAll() {
        return Result.success(repository.findAll());
    }

    @GetMapping("/{id}")
    public Result<PriceTable> getById(@PathVariable Long id) {
        return Result.success(repository.findById(id).orElse(null));
    }

    @PostMapping
    public Result<PriceTable> create(@RequestBody PriceTable priceTable) {
        return Result.success(repository.save(priceTable));
    }

    /**
     * 更新单价并同步更新历史计件记录
     */
    @Transactional
    @PutMapping("/{id}")
    public Result<PriceTable> update(@PathVariable Long id, @RequestBody PriceTable priceTable) {
        PriceTable existing = repository.findById(id).orElse(null);
        if (existing == null) {
            return Result.error("单价记录不存在");
        }

        BigDecimal oldPrice = existing.getUnitPrice();
        BigDecimal newPrice = priceTable.getUnitPrice();
        String productName = existing.getProductName();

        // 保存新单价
        priceTable.setId(id);
        PriceTable saved = repository.save(priceTable);

        // 如果单价发生变化，更新所有历史计件记录
        if (oldPrice != null && newPrice != null && oldPrice.compareTo(newPrice) != 0) {
            System.out.println("==========================================");
            System.out.println("单价变更，更新历史计件记录");
            System.out.println("产品: " + productName);
            System.out.println("旧单价: " + oldPrice + " -> 新单价: " + newPrice);

            int updatedCount = updatePieceWorkRecords(productName, newPrice);
            System.out.println("已更新 " + updatedCount + " 条计件记录");
            System.out.println("==========================================");
        }

        return Result.success(saved);
    }

    /**
     * 更新指定产品的所有计件记录的单价和金额
     */
    private int updatePieceWorkRecords(String productName, BigDecimal newPrice) {
        List<PieceWork> records = pieceWorkRepository.findByProductName(productName);
        int count = 0;
        for (PieceWork record : records) {
            record.setUnitPrice(newPrice);
            // 重新计算金额 = 数量 × 新单价
            if (record.getQuantity() != null) {
                BigDecimal newAmount = newPrice.multiply(BigDecimal.valueOf(record.getQuantity()));
                record.setTotalAmount(newAmount);
            }
            pieceWorkRepository.save(record);
            count++;
        }
        return count;
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return Result.success();
    }

    @GetMapping("/product/{productName}")
    public Result<List<PriceTable>> getByProduct(@PathVariable String productName) {
        return Result.success(repository.findByProductNameAndIsActive(productName, true));
    }
}

