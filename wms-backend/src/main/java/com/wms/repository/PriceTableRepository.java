package com.wms.repository;
import com.wms.entity.PriceTable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PriceTableRepository extends JpaRepository<PriceTable, Long> {
    List<PriceTable> findByProductNameAndIsActive(String productName, Boolean isActive);
    List<PriceTable> findByIsActive(Boolean isActive);
}
