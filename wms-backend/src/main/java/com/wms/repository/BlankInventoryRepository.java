package com.wms.repository;

import com.wms.entity.BlankInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 毛坯库存Repository
 */
@Repository
public interface BlankInventoryRepository extends JpaRepository<BlankInventory, Long> {
    
    /**
     * 查找指定产品的毛坯库存
     */
    Optional<BlankInventory> findByProductNameAndSpecificationAndMaterial(
        String productName, 
        String specification, 
        String material
    );
    
    /**
     * 按产品名称查找
     */
    List<BlankInventory> findByProductName(String productName);
    
    /**
     * 查找所有库存，按创建时间倒序
     */
    List<BlankInventory> findAllByOrderByCreatedAtDesc();
}
