package com.wms.repository;

import com.wms.entity.InventoryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
    List<InventoryLog> findByPieceworkId(Long pieceworkId);
    List<InventoryLog> findByRuleId(Long ruleId);
    void deleteByPieceworkId(Long pieceworkId);
}

