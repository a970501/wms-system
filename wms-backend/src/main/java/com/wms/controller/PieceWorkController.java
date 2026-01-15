package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.annotation.Auditable;
import com.wms.entity.PieceWork;
import com.wms.service.PieceWorkService;
import com.wms.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/piecework")
public class PieceWorkController {

    private static final Logger log = LoggerFactory.getLogger(PieceWorkController.class);

    @Autowired
    private PieceWorkService service;

    @RequireAuth
    @GetMapping
    public Result<?> getAll(
            HttpServletRequest request,
            @RequestParam(required = false) String workerName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "false") Boolean queryAll,
            @RequestParam(required = false, defaultValue = "false") Boolean advancedSearch) {

        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");

        // 高级查询页面允许所有用户查询全部数据（用于统计和筛选）
        // 普通查询页面仍然限制普通用户只能查看自己的数据
        boolean allowQueryAll = Boolean.TRUE.equals(advancedSearch) || "ADMIN".equals(role);

        if (Boolean.TRUE.equals(queryAll) && !allowQueryAll) {
            return Result.error(403, "无权限：仅管理员可查询全部计件记录");
        }

        // 如果是高级查询，允许查询所有数据
        Boolean effectiveQueryAll = allowQueryAll && Boolean.TRUE.equals(queryAll);

        Object data = service.search(role, username, workerName, effectiveQueryAll, startDate, endDate, page, size);
        return Result.success(data);
    }

    @RequireAuth
    @GetMapping("/reconcile")
    public Result<?> reconcile(
            HttpServletRequest request,
            @RequestParam String userA,
            @RequestParam String userB,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false, defaultValue = "true") Boolean includeUnitPrice,
            @RequestParam(required = false, defaultValue = "true") Boolean includeConnectionType) {

        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return Result.error(403, "无权限：仅管理员可对账");
        }

        Map<String, Object> data = service.reconcile(userA, userB, startDate, endDate,
                Boolean.TRUE.equals(includeUnitPrice), Boolean.TRUE.equals(includeConnectionType));
        return Result.success(data);
    }

    @RequireAuth
    @GetMapping("/{id:\\d+}")
    public Result<PieceWork> getById(@PathVariable Long id, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");
        PieceWork record = service.findById(id);
        // 非管理员只能查看自己的记录
        if (!"ADMIN".equals(role) && record != null && !username.equals(record.getWorkerName())) {
            return Result.error(403, "无权限查看他人记录");
        }
        return Result.success(record);
    }

    @RequireAuth
    @Auditable(module = "计件管理", action = "新增计件记录")
    @PostMapping
    public Result<PieceWork> create(@RequestBody PieceWork pieceWork, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        log.debug("Create piecework - Role: {}, Product: {}, Qty: {}", role, pieceWork.getProductName(), pieceWork.getQuantity());
        PieceWork result = service.create(pieceWork, role);
        return Result.success(result);
    }

    @RequireAuth
    @Auditable(module = "计件管理", action = "修改计件记录")
    @PutMapping("/{id}")
    public Result<PieceWork> update(@PathVariable Long id, @RequestBody PieceWork pieceWork, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");
        log.debug("Update piecework - ID: {}, Role: {}, User: {}", id, role, username);
        pieceWork.setId(id);
        PieceWork result = service.update(pieceWork, role);
        return Result.success(result);
    }

    @RequireAuth
    @Auditable(module = "计件管理", action = "删除计件记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        log.debug("Delete piecework - ID: {}, Role: {}", id, role);
        service.deleteWithRollback(id, role);
        return Result.success();
    }

    @RequireAuth
    @GetMapping("/worker/{workerName}")
    public Result<List<PieceWork>> getByWorker(
            @PathVariable String workerName,
            HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");
        // 非管理员只能查看自己的记录
        if (!"ADMIN".equals(role) && !username.equals(workerName)) {
            return Result.error(403, "无权限查看他人记录");
        }
        return Result.success(service.findByWorkerName(workerName));
    }
}

