package com.wms.repository;

import com.wms.entity.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备份记录数据访问接口
 */
@Repository
public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {
    
    /**
     * 按创建时间倒序查询所有备份
     */
    List<BackupRecord> findAllByOrderByCreatedAtDesc();
    
    /**
     * 按类型查询
     */
    List<BackupRecord> findByType(String type);
    
    /**
     * 按状态查询
     */
    List<BackupRecord> findByStatus(String status);
    
    /**
     * 查询指定时间之前的备份
     */
    List<BackupRecord> findByCreatedAtBefore(LocalDateTime dateTime);
    
    /**
     * 查询自动备份
     */
    List<BackupRecord> findByIsAutomatic(Boolean isAutomatic);
    
    /**
     * 统计备份总数
     */
    long countByStatus(String status);
}
