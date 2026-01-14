package com.wms.repository;

import com.wms.entity.FinishedProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FinishedProductRepository extends JpaRepository<FinishedProduct, Long> {
    
    Optional<FinishedProduct> findByProductNameAndSpecificationAndMaterialAndConnectionType(
        String productName, String specification, String material, String connectionType);
}
