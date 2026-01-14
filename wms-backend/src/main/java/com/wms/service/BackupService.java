package com.wms.service;

import com.wms.entity.BackupRecord;
import com.wms.repository.BackupRecordRepository;
import com.wms.dto.BackupScheduleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 备份服务
 * 性能优化：支持异步备份，避免阻塞HTTP线程
 */
@Service
public class BackupService {
    
    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    @Autowired
    private BackupRecordRepository backupRepository;
    
    @Value("${backup.directory:/opt/app/wms/backups}")
    private String backupDirectory;
    
    @Value("${spring.datasource.url}")
    private String dbUrl;
    
    @Value("${spring.datasource.username}")
    private String dbUsername;
    
    @Value("${spring.datasource.password}")
    private String dbPassword;
    
    @Value("${backup.frontend.path:/www/wwwroot/vue3-wms}")
    private String frontendPath;
    
    @Value("${backup.backend.path:/opt/app/wms/backend}")
    private String backendPath;
    
    /**
     * 获取所有备份记录（按创建时间倒序）
     */
    public List<BackupRecord> getAllBackups() {
        return backupRepository.findAllByOrderByCreatedAtDesc();
    }
    
    /**
     * 根据ID获取备份记录
     */
    public BackupRecord getBackupById(Long id) {
        return backupRepository.findById(id).orElse(null);
    }
    
    /**
     * 保存备份记录
     */
    public BackupRecord saveBackup(BackupRecord backup) {
        return backupRepository.save(backup);
    }
    
    /**
     * 创建新备份
     */
    @Transactional
    public BackupRecord createBackup(boolean includeDatabase, boolean includeFiles, String description) {
        logger.info("开始创建备份 - 数据库: {}, 文件: {}", includeDatabase, includeFiles);
        
        // 确保备份目录存在
        File backupDir = new File(backupDirectory);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String backupType = determineBackupType(includeDatabase, includeFiles);
        
        // 创建备份记录
        BackupRecord record = new BackupRecord();
        record.setName("backup-" + timestamp);
        record.setType(backupType);
        record.setStatus("running");
        record.setDescription(description);
        record.setCreatedAt(LocalDateTime.now());
        record = backupRepository.save(record);
        
        try {
            List<String> backupFiles = new ArrayList<>();
            
            // 备份数据库
            if (includeDatabase) {
                String dbBackupFile = backupDatabase(timestamp);
                if (dbBackupFile != null) {
                    backupFiles.add(dbBackupFile);
                }
            }
            
            // 备份文件
            if (includeFiles) {
                String filesBackupFile = backupFiles(timestamp);
                if (filesBackupFile != null) {
                    backupFiles.add(filesBackupFile);
                }
            }
            
            // 创建最终压缩包
            String finalBackupPath = createFinalBackup(timestamp, backupFiles);
            
            // 更新记录
            record.setFilePath(finalBackupPath);
            record.setSize(new File(finalBackupPath).length());
            record.setStatus("completed");
            record.setCompletedAt(LocalDateTime.now());
            
            // 清理临时文件
            cleanupTemporaryFiles(backupFiles);
            
            logger.info("备份创建成功: {}", finalBackupPath);
            
        } catch (Exception e) {
            logger.error("备份创建失败", e);
            record.setStatus("failed");
            record.setErrorMessage(e.getMessage());
        }
        
        return backupRepository.save(record);
    }
    
    /**
     * 异步创建备份（性能优化）
     * 不阻塞HTTP请求线程，适合大文件备份
     */
    @Async
    public void createBackupAsync(Long backupId, boolean includeDatabase, boolean includeFiles) {
        logger.info("异步备份任务开始: backupId={}", backupId);
        
        try {
            BackupRecord record = backupRepository.findById(backupId)
                .orElseThrow(() -> new RuntimeException("备份记录不存在"));
            
            record.setStatus("processing");
            backupRepository.save(record);
            
            String timestamp = LocalDateTime.now().format(DATE_FORMAT);
            List<String> backupFiles = new ArrayList<>();
            
            // 备份数据库
            if (includeDatabase) {
                String dbBackupFile = backupDatabase(timestamp);
                if (dbBackupFile != null) {
                    backupFiles.add(dbBackupFile);
                }
            }
            
            // 备份文件
            if (includeFiles) {
                String filesBackupFile = backupFiles(timestamp);
                if (filesBackupFile != null) {
                    backupFiles.add(filesBackupFile);
                }
            }
            
            // 创建最终压缩包
            String finalBackupPath = createFinalBackup(timestamp, backupFiles);
            
            // 更新记录
            record.setFilePath(finalBackupPath);
            record.setSize(new File(finalBackupPath).length());
            record.setStatus("completed");
            record.setCompletedAt(LocalDateTime.now());
            backupRepository.save(record);
            
            // 清理临时文件
            cleanupTemporaryFiles(backupFiles);
            
            logger.info("异步备份任务完成: backupId={}, path={}", backupId, finalBackupPath);
            
        } catch (Exception e) {
            logger.error("异步备份失败: backupId={}", backupId, e);
            try {
                BackupRecord record = backupRepository.findById(backupId).orElse(null);
                if (record != null) {
                    record.setStatus("failed");
                    record.setErrorMessage(e.getMessage());
                    backupRepository.save(record);
                }
            } catch (Exception ex) {
                logger.error("更新备份失败状态异常", ex);
            }
        }
    }
    
    /**
     * 备份数据库
     * 安全优化：使用环境变量传递密码，避免密码暴露在命令行
     */
    private String backupDatabase(String timestamp) {
        try {
            // 从JDBC URL中提取数据库名
            String dbName = extractDatabaseName(dbUrl);
            String backupFile = backupDirectory + "/db-" + timestamp + ".sql";
            
            logger.info("开始备份数据库: {} -> {}", dbName, backupFile);
            
            // 构建mysqldump命令（不在命令行传递密码）
            ProcessBuilder pb = new ProcessBuilder(
                "mysqldump",
                "-u" + dbUsername,
                "--single-transaction",
                "--routines",
                "--triggers",
                dbName
            );
            
            // 使用环境变量传递密码，避免在进程列表中暴露
            Map<String, String> env = pb.environment();
            env.put("MYSQL_PWD", dbPassword);
            
            pb.redirectOutput(new File(backupFile));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("数据库备份成功: {}", backupFile);
                return backupFile;
            } else {
                logger.error("数据库备份失败，退出码: {}", exitCode);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("数据库备份异常", e);
            return null;
        }
    }
    
    /**
     * 备份文件（前端+后端）
     */
    private String backupFiles(String timestamp) {
        try {
            String backupFile = backupDirectory + "/files-" + timestamp + ".tar.gz";
            
            logger.info("开始备份文件 -> {}", backupFile);
            
            // 使用tar命令打包
            ProcessBuilder pb = new ProcessBuilder(
                "tar",
                "-czf",
                backupFile,
                "-C", "/www/wwwroot", "vue3-wms",
                "-C", "/opt/app/wms", "backend"
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("文件备份成功: {}", backupFile);
                return backupFile;
            } else {
                logger.error("文件备份失败，退出码: {}", exitCode);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("文件备份异常", e);
            return null;
        }
    }
    
    /**
     * 创建最终备份包
     */
    private String createFinalBackup(String timestamp, List<String> files) throws IOException {
        String finalBackupPath = backupDirectory + "/wms-backup-" + timestamp + ".tar.gz";
        
        if (files.size() == 1) {
            // 只有一个文件，直接重命名
            Files.move(
                Paths.get(files.get(0)), 
                Paths.get(finalBackupPath),
                StandardCopyOption.REPLACE_EXISTING
            );
        } else {
            // 多个文件，打包在一起
            ProcessBuilder pb = new ProcessBuilder();
            List<String> command = new ArrayList<>();
            command.add("tar");
            command.add("-czf");
            command.add(finalBackupPath);
            command.addAll(files);
            
            pb.command(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("备份打包被中断", e);
            }
        }
        
        return finalBackupPath;
    }
    
    /**
     * 清理临时文件
     */
    private void cleanupTemporaryFiles(List<String> files) {
        for (String file : files) {
            try {
                Files.deleteIfExists(Paths.get(file));
            } catch (IOException e) {
                logger.warn("清理临时文件失败: {}", file, e);
            }
        }
    }
    
    /**
     * 删除备份
     */
    @Transactional
    public void deleteBackup(Long id) {
        BackupRecord backup = backupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("备份记录不存在"));
        
        // 删除物理文件
        if (backup.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(backup.getFilePath()));
                logger.info("删除备份文件: {}", backup.getFilePath());
            } catch (IOException e) {
                logger.error("删除备份文件失败", e);
            }
        }
        
        // 删除数据库记录
        backupRepository.deleteById(id);
    }
    
    /**
     * 批量删除备份
     */
    @Transactional
    public void batchDeleteBackups(List<Long> ids) {
        for (Long id : ids) {
            try {
                deleteBackup(id);
            } catch (Exception e) {
                logger.error("删除备份失败: id={}", id, e);
            }
        }
    }
    
    /**
     * 清理过期备份
     */
    @Transactional
    public int cleanupOldBackups(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<BackupRecord> oldBackups = backupRepository.findByCreatedAtBefore(cutoffDate);
        
        int count = 0;
        for (BackupRecord backup : oldBackups) {
            try {
                deleteBackup(backup.getId());
                count++;
            } catch (Exception e) {
                logger.error("清理过期备份失败", e);
            }
        }
        
        logger.info("清理了 {} 个过期备份", count);
        return count;
    }
    
    /**
     * 获取定时备份配置
     */
    public BackupScheduleConfig getScheduleConfig() {
        // 从内存缓存或默认配置读取
        // 生产环境应该从数据库的backup_config表读取
        if (scheduleConfigCache == null) {
            scheduleConfigCache = new BackupScheduleConfig();
            scheduleConfigCache.setEnabled(false);
            scheduleConfigCache.setFrequency("daily");
            scheduleConfigCache.setTime("02:00");
            scheduleConfigCache.setRetentionDays(30);
            scheduleConfigCache.setIncludeDatabase(true);
            scheduleConfigCache.setIncludeFiles(true);
        }
        return scheduleConfigCache;
    }
    
    // 配置缓存
    private static BackupScheduleConfig scheduleConfigCache = null;
    
    /**
     * 保存定时备份配置
     */
    public void saveScheduleConfig(BackupScheduleConfig config) {
        logger.info("保存定时备份配置: enabled={}, frequency={}, time={}", 
            config.getEnabled(), config.getFrequency(), config.getTime());
        
        // 保存到内存缓存
        scheduleConfigCache = config;
        
        // TODO: 持久化到数据库backup_config表
        // 示例SQL:
        // INSERT INTO backup_config (config_key, config_value, updated_at)
        // VALUES ('schedule_config', JSON, NOW())
        // ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = NOW()
        
        logger.info("定时备份配置已保存到缓存");
    }
    
    /**
     * 获取备份统计信息
     */
    public Map<String, Object> getStatistics() {
        List<BackupRecord> allBackups = backupRepository.findAll();
        
        long totalSize = allBackups.stream()
                .filter(b -> b.getSize() != null)
                .mapToLong(BackupRecord::getSize)
                .sum();
        
        long dbCount = allBackups.stream()
                .filter(b -> "database".equals(b.getType()) || "full".equals(b.getType()))
                .count();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allBackups.size());
        stats.put("totalSize", totalSize);
        stats.put("dbCount", dbCount);
        stats.put("latestBackup", allBackups.isEmpty() ? null : 
                allBackups.get(0).getCreatedAt());
        
        return stats;
    }
    
    /**
     * 确定备份类型
     */
    private String determineBackupType(boolean includeDatabase, boolean includeFiles) {
        if (includeDatabase && includeFiles) {
            return "full";
        } else if (includeDatabase) {
            return "database";
        } else if (includeFiles) {
            return "files";
        }
        return "unknown";
    }
    
    /**
     * 从JDBC URL提取数据库名
     */
    private String extractDatabaseName(String jdbcUrl) {
        // jdbc:mysql://localhost:3306/warehouse_db?serverTimezone=Asia/Shanghai
        // 找到第三个'/'后面的内容作为数据库名开始位置
        int thirdSlash = jdbcUrl.indexOf('/', jdbcUrl.indexOf('/', jdbcUrl.indexOf('/') + 1) + 1);
        if (thirdSlash < 0) {
            return "warehouse_db"; // 默认值
        }
        
        String afterThirdSlash = jdbcUrl.substring(thirdSlash + 1);
        int questionMark = afterThirdSlash.indexOf('?');
        
        if (questionMark > 0) {
            return afterThirdSlash.substring(0, questionMark);
        } else {
            return afterThirdSlash;
        }
    }
}
