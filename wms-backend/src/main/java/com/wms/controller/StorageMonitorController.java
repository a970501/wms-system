package com.wms.controller;

import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/storage-logs")
@CrossOrigin
public class StorageMonitorController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取入库日志列表
     */
    @GetMapping
    public Result<List<Map<String, Object>>> getLogs(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        
        int offset = page * size;
        
        String sql = "SELECT id, log_date, semi_finished_count, semi_finished_quantity, " +
                     "storage_count, storage_quantity, worker_count, product_types, " +
                     "failed_count, created_at " +
                     "FROM storage_monitor_logs " +
                     "ORDER BY log_date DESC LIMIT ? OFFSET ?";
        
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql, size, offset);
        
        return Result.success(logs);
    }

    /**
     * 获取日志详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getLogDetail(@PathVariable Long id) {
        String sql = "SELECT * FROM storage_monitor_logs WHERE id = ?";
        Map<String, Object> log = jdbcTemplate.queryForMap(sql, id);
        
        // 获取未入库记录详情
        String failedSql = "SELECT * FROM failed_storage_records WHERE log_id = ? ORDER BY work_date DESC";
        List<Map<String, Object>> failedRecords = jdbcTemplate.queryForList(failedSql, id);
        log.put("failed_records", failedRecords);
        
        return Result.success(log);
    }

    /**
     * 获取统计数据
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        String sql = "SELECT " +
                     "COUNT(*) as total_days, " +
                     "SUM(semi_finished_count) as total_semi, " +
                     "SUM(storage_count) as total_storage, " +
                     "SUM(failed_count) as total_failed, " +
                     "ROUND(SUM(storage_count) * 100.0 / NULLIF(SUM(semi_finished_count), 0), 2) as success_rate " +
                     "FROM storage_monitor_logs " +
                     "WHERE log_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)";
        
        Map<String, Object> stats = jdbcTemplate.queryForMap(sql);
        
        return Result.success(stats);
    }

    /**
     * 获取最近7天趋势
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrend() {
        String sql = "SELECT log_date, semi_finished_count, storage_count, failed_count, " +
                     "ROUND(storage_count * 100.0 / NULLIF(semi_finished_count, 0), 2) as success_rate " +
                     "FROM storage_monitor_logs " +
                     "WHERE log_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                     "ORDER BY log_date ASC";
        
        List<Map<String, Object>> trend = jdbcTemplate.queryForList(sql);
        
        return Result.success(trend);
    }
}
