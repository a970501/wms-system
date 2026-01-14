package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.dto.AssemblyCheckRequest;
import com.wms.dto.AssemblyCheckResult;
import com.wms.entity.AssemblyRecord;
import com.wms.service.AssemblyService;
import com.wms.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/assembly-records")
@RequireAuth
public class AssemblyRecordController {
    
    @Autowired
    private AssemblyService assemblyService;
    
    @GetMapping
    public Result<List<AssemblyRecord>> getAll() {
        return Result.success(assemblyService.findAll());
    }
    
    @GetMapping("/{id}")
    public Result<AssemblyRecord> getById(@PathVariable Long id) {
        return Result.success(assemblyService.findById(id));
    }
    
    @PostMapping
    public Result<AssemblyRecord> create(@RequestBody AssemblyRecord record) {
        try {
            AssemblyRecord result = assemblyService.executeAssembly(record);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/check")
    public Result<AssemblyCheckResult> check(@RequestBody AssemblyCheckRequest request) {
        return Result.success(assemblyService.checkAssembly(request));
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assemblyService.deleteById(id);
        return Result.success();
    }
}
