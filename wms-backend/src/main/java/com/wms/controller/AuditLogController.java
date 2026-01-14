package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit-logs")
@CrossOrigin
public class AuditLogController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取操作日志列表（分页）
     * 仅管理员可查看所有日志，普通用户只能查看自己的
     */
    @RequireAuth
    @GetMapping
    public Result<Map<String, Object>> getLogs(
            HttpServletRequest request,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        
        String role = (String) request.getAttribute("role");
        String currentUser = (String) request.getAttribute("username");
        
        StringBuilder sql = new StringBuilder(
            "SELECT id, username, module, action, details, ip_address, created_at " +
            "FROM audit_logs WHERE 1=1"
        );
        
        // 普通用户只能看自己的日志
        if (!"ADMIN".equals(role)) {
            sql.append(" AND username = ?");
            username = currentUser;
        } else if (username != null && !username.isEmpty()) {
            sql.append(" AND username = ?");
        }
        
        if (module != null && !module.isEmpty()) {
            sql.append(" AND module = ?");
        }
        
        if (action != null && !action.isEmpty()) {
            sql.append(" AND action LIKE ?");
        }
        
        if (startDate != null && !startDate.isEmpty()) {
            sql.append(" AND DATE(created_at) >= ?");
        }
        
        if (endDate != null && !endDate.isEmpty()) {
            sql.append(" AND DATE(created_at) <= ?");
        }
        
        // 构建参数
        List<Object> params = new java.util.ArrayList<>();
        if (username != null && !username.isEmpty()) params.add(username);
        if (module != null && !module.isEmpty()) params.add(module);
        if (action != null && !action.isEmpty()) params.add("%" + action + "%");
        if (startDate != null && !startDate.isEmpty()) params.add(startDate);
        if (endDate != null && !endDate.isEmpty()) params.add(endDate);
        
        // 获取总数
        String countSql = sql.toString().replace(
            "SELECT id, username, module, action, details, ip_address, created_at",
            "SELECT COUNT(*)"
        );
        int total = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
        
        // 分页查询
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", logs);
        result.put("totalElements", total);
        result.put("totalPages", (int) Math.ceil((double) total / size));
        result.put("number", page);
        result.put("size", size);
        
        return Result.success(result);
    }

    /**
     * 获取操作统计
     */
    @RequireAuth
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");
        
        String whereClause = "ADMIN".equals(role) ? "" : "WHERE username = ?";
        
        // 总操作数
        String totalSql = "SELECT COUNT(*) FROM audit_logs " + whereClause;
        Integer total = "ADMIN".equals(role) 
            ? jdbcTemplate.queryForObject(totalSql, Integer.class)
            : jdbcTemplate.queryForObject(totalSql, Integer.class, username);
        
        // 今日操作数
        String todaySql = "SELECT COUNT(*) FROM audit_logs WHERE DATE(created_at) = CURDATE() " +
                         ("ADMIN".equals(role) ? "" : "AND username = ?");
        Integer today = "ADMIN".equals(role)
            ? jdbcTemplate.queryForObject(todaySql, Integer.class)
            : jdbcTemplate.queryForObject(todaySql, Integer.class, username);
        
        // 按模块统计
        String moduleSql = "SELECT module, COUNT(*) as count FROM audit_logs " + 
                          whereClause + " GROUP BY module ORDER BY count DESC LIMIT 10";
        List<Map<String, Object>> byModule = "ADMIN".equals(role)
            ? jdbcTemplate.queryForList(moduleSql)
            : jdbcTemplate.queryForList(moduleSql, username);
        
        // 按操作统计
        String actionSql = "SELECT action, COUNT(*) as count FROM audit_logs " + 
                          whereClause + " GROUP BY action ORDER BY count DESC LIMIT 10";
        List<Map<String, Object>> byAction = "ADMIN".equals(role)
            ? jdbcTemplate.queryForList(actionSql)
            : jdbcTemplate.queryForList(actionSql, username);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("today", today);
        stats.put("byModule", byModule);
        stats.put("byAction", byAction);
        
        return Result.success(stats);
    }

    /**
     * 获取最近7天趋势
     */
    @RequireAuth
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrend(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");
        
        String sql = "SELECT DATE(created_at) as date, COUNT(*) as count " +
                    "FROM audit_logs " +
                    "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                    ("ADMIN".equals(role) ? "" : "AND username = ? ") +
                    "GROUP BY DATE(created_at) ORDER BY date ASC";
        
        List<Map<String, Object>> trend = "ADMIN".equals(role)
            ? jdbcTemplate.queryForList(sql)
            : jdbcTemplate.queryForList(sql, username);
        
        return Result.success(trend);
    }

    /**
     * 获取模块列表
     */
    @RequireAuth
    @GetMapping("/modules")
    public Result<List<String>> getModules() {
        String sql = "SELECT DISTINCT module FROM audit_logs ORDER BY module";
        List<String> modules = jdbcTemplate.queryForList(sql, String.class);
        return Result.success(modules);
    }
}
