package com.wms.repository;

import com.wms.entity.AssemblyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssemblyRecordRepository extends JpaRepository<AssemblyRecord, Long> {
    List<AssemblyRecord> findByProductName(String productName);
    List<AssemblyRecord> findByOperator(String operator);
    List<AssemblyRecord> findByStatus(String status);
}
