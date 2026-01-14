package com.wms.repository;

import com.wms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUsernameOrderByCreatedAtDesc(String username);
    List<AuditLog> findByModuleOrderByCreatedAtDesc(String module);
    List<AuditLog> findAllByOrderByCreatedAtDesc();
    List<AuditLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
}
