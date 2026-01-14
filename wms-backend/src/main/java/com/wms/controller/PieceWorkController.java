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
            @RequestParam(required = false, defaultValue = "false") Boolean queryAll) {

        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");

        if (Boolean.TRUE.equals(queryAll) && !"ADMIN".equals(role)) {
            return Result.error(403, "无权限：仅管理员可查询全部计件记录");
        }

        Object data = service.search(role, username, workerName, queryAll, startDate, endDate, page, size);
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

