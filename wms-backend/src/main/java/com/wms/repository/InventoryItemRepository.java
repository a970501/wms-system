package com.wms.repository;

import com.wms.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> { 
    List<InventoryItem> findByProductNameContaining(String productName);
    
    Optional<InventoryItem> findByProductNameAndSpecificationAndMaterialAndConnectionType(
        String productName, String specification, String material, String connectionType);
}
