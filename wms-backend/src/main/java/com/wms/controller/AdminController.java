package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.annotation.RequireRole;
import com.wms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequireAuth
@RequireRole({"ADMIN"})
public class AdminController {

    @Autowired
    private PieceWorkRepository pieceWorkRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private AssemblyRecordRepository assemblyRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * 清空计件记录
     */
    @DeleteMapping("/cleanup/piecework")
    @Transactional
    public ResponseEntity<?> cleanupPiecework() {
        try {
            long count = pieceWorkRepository.count();
            pieceWorkRepository.deleteAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "计件记录已清空");
            response.put("deletedCount", count);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 清空库存记录
     */
    @DeleteMapping("/cleanup/inventory")
    @Transactional
    public ResponseEntity<?> cleanupInventory() {
        try {
            long count = inventoryItemRepository.count();
            inventoryItemRepository.deleteAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "库存记录已清空");
            response.put("deletedCount", count);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 清空组装记录
     */
    @DeleteMapping("/cleanup/assembly")
    @Transactional
    public ResponseEntity<?> cleanupAssembly() {
        try {
            long count = assemblyRecordRepository.count();
            assemblyRecordRepository.deleteAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "组装记录已清空");
            response.put("deletedCount", count);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 清空操作记录
     */
    @DeleteMapping("/cleanup/audit-logs")
    @Transactional
    public ResponseEntity<?> cleanupAuditLogs() {
        try {
            long count = auditLogRepository.count();
            auditLogRepository.deleteAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "操作记录已清空");
            response.put("deletedCount", count);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取服务器运行时间（毫秒）
     */
    @GetMapping("/server-uptime")
    public ResponseEntity<?> getServerUptime() {
        try {
            // 获取JVM运行时间（毫秒）
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            
            Map<String, Object> response = new HashMap<>();
            response.put("uptime", uptime);
            response.put("uptimeSeconds", uptime / 1000);
            response.put("uptimeMinutes", uptime / (1000 * 60));
            response.put("uptimeHours", uptime / (1000 * 60 * 60));
            response.put("uptimeDays", uptime / (1000 * 60 * 60 * 24));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取服务器运行时间失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取服务器信息（用于监控）
     */
    @GetMapping("/server-info")
    public ResponseEntity<?> getServerInfo() {
        try {
            Runtime runtime = Runtime.getRuntime();
            
            Map<String, Object> info = new HashMap<>();
            
            // CPU和内存信息
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            info.put("maxMemory", maxMemory);
            info.put("totalMemory", totalMemory);
            info.put("freeMemory", freeMemory);
            info.put("usedMemory", usedMemory);
            info.put("memoryUsage", (int)((usedMemory * 100.0) / maxMemory));
            
            // 处理器数量
            info.put("processors", runtime.availableProcessors());
            
            // JVM运行时间（毫秒）
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            info.put("uptime", uptime);
            
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取服务器信息失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取数据库统计信息
     */
    @GetMapping("/database-stats")
    public ResponseEntity<?> getDatabaseStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("pieceworkCount", pieceWorkRepository.count());
            stats.put("inventoryCount", inventoryItemRepository.count());
            stats.put("assemblyCount", assemblyRecordRepository.count());
            stats.put("auditLogCount", auditLogRepository.count());
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取数据库统计失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
