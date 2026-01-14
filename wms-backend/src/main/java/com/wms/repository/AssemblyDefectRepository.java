package com.wms.repository;

import com.wms.entity.AssemblyDefect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssemblyDefectRepository extends JpaRepository<AssemblyDefect, Long> {
    List<AssemblyDefect> findByAssemblyRecordId(Long assemblyRecordId);
}
