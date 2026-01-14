package com.wms.controller;

import com.wms.common.Result;
import com.wms.entity.BackupRecord;
import com.wms.service.BackupService;
import com.wms.dto.BackupScheduleConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 备份管理控制器
 */
@RestController
@RequestMapping("/backups")
@CrossOrigin(origins = "*")
public class BackupController {
    
    @Autowired
    private BackupService backupService;
    @Autowired
    private com.wms.repository.PieceWorkRepository pieceWorkRepository;
    
    @Autowired
    private com.wms.repository.InventoryItemRepository inventoryRepository;
    
    @Autowired
    private com.wms.repository.BlankInventoryRepository blankInventoryRepository;
    
    @Autowired
    private com.wms.repository.PriceTableRepository priceTableRepository;
    
    @Autowired
    private com.wms.repository.AutoStorageRuleRepository autoStorageRuleRepository;
    
    @Autowired
    private com.wms.repository.AssemblyRuleRepository assemblyRuleRepository;
    
    /**
     * 获取所有备份记录
     */
    @GetMapping
    public Result<List<BackupRecord>> getAllBackups() {
        try {
            List<BackupRecord> backups = backupService.getAllBackups();
            return Result.success(backups);
        } catch (Exception e) {
            return Result.error("获取备份列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取单个备份记录详情
     */
    @GetMapping("/{id}")
    public Result<BackupRecord> getBackupById(@PathVariable Long id) {
        try {
            BackupRecord backup = backupService.getBackupById(id);
            if (backup == null) {
                return Result.error("备份记录不存在");
            }
            return Result.success(backup);
        } catch (Exception e) {
            return Result.error("获取备份详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建新备份（异步执行，立即返回）
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> createBackup(@RequestBody Map<String, Object> request) {
        try {
            boolean includeDatabase = (Boolean) request.getOrDefault("includeDatabase", true);
            boolean includeFiles = (Boolean) request.getOrDefault("includeFiles", true);
            String description = (String) request.get("description");
            
            // 创建初始记录
            BackupRecord backup = new BackupRecord();
            backup.setName("backup-" + java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            backup.setType(determineBackupType(includeDatabase, includeFiles));
            backup.setStatus("pending");
            backup.setDescription(description);
            backup.setCreatedAt(java.time.LocalDateTime.now());
            backup = backupService.saveBackup(backup);
            
            // 异步执行备份
            backupService.createBackupAsync(backup.getId(), includeDatabase, includeFiles);
            
            // 立即返回
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("id", backup.getId());
            result.put("status", "pending");
            result.put("message", "备份任务已启动，请稍候刷新查看进度");
            
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("创建备份失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载备份文件
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadBackup(
            @PathVariable Long id,
            @RequestParam(required = false) String token) {
        try {
            BackupRecord backup = backupService.getBackupById(id);
            if (backup == null) {
                return ResponseEntity.notFound().build();
            }

            File file = new File(backup.getFilePath());
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            
            // 确保文件名有.zip扩展名
            String filename = backup.getName();
            if (!filename.toLowerCase().endsWith(".zip")) {
                filename = filename + ".zip";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            System.err.println("下载备份失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除备份
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteBackup(@PathVariable Long id) {
        try {
            backupService.deleteBackup(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除备份失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量删除备份
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteBackups(@RequestBody List<Long> ids) {
        try {
            backupService.batchDeleteBackups(ids);
            return Result.success();
        } catch (Exception e) {
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取定时备份配置
     */
    @GetMapping("/schedule")
    public Result<BackupScheduleConfig> getScheduleConfig() {
        try {
            BackupScheduleConfig config = backupService.getScheduleConfig();
            return Result.success(config);
        } catch (Exception e) {
            return Result.error("获取定时配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存定时备份配置
     */
    @PostMapping("/schedule")
    public Result<Void> saveScheduleConfig(@RequestBody BackupScheduleConfig config) {
        try {
            backupService.saveScheduleConfig(config);
            return Result.success();
        } catch (Exception e) {
            return Result.error("保存定时配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理过期备份
     */
    @PostMapping("/cleanup")
    public Result<Integer> cleanupOldBackups(@RequestParam(defaultValue = "30") int days) {
        try {
            int count = backupService.cleanupOldBackups(days);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error("清理失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取备份统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = backupService.getStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error("获取统计信息失败: " + e.getMessage());
        }
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
     * 下载系统日志
    /**
     * 下载系统日志
    /**
     * 下载系统日志（TXT格式）
     */
    @GetMapping("/logs/download")
    public ResponseEntity<Resource> downloadLogs() {
        try {
            StringBuilder logContent = new StringBuilder();
            logContent.append("=".repeat(80)).append("\n");
            logContent.append("系统日志导出 - ").append(java.time.LocalDateTime.now()).append("\n");
            logContent.append("=".repeat(80)).append("\n\n");
            
            // 1. 添加应用日志
            logContent.append("\n").append("=".repeat(80)).append("\n");
            logContent.append("应用日志 (Application Logs)\n");
            logContent.append("=".repeat(80)).append("\n\n");
            
            java.nio.file.Path logsDir = java.nio.file.Paths.get("/opt/app/wms/logs");
            if (java.nio.file.Files.exists(logsDir)) {
                java.nio.file.Files.walk(logsDir)
                    .filter(path -> path.toString().endsWith(".log"))
                    .forEach(path -> {
                        try {
                            String filename = path.getFileName().toString();
                            logContent.append("\n--- ").append(filename).append(" ---\n\n");
                            
                            // 读取最后1000行
                            java.util.List<String> lines = java.nio.file.Files.readAllLines(path);
                            int startLine = Math.max(0, lines.size() - 1000);
                            for (int i = startLine; i < lines.size(); i++) {
                                logContent.append(lines.get(i)).append("\n");
                            }
                            logContent.append("\n");
                            
                            System.out.println("已添加日志文件: " + filename + " (共" + (lines.size() - startLine) + "行)");
                        } catch (Exception e) {
                            logContent.append("读取失败: ").append(e.getMessage()).append("\n\n");
                            System.err.println("读取日志文件失败: " + path + " - " + e.getMessage());
                        }
                    });
            } else {
                logContent.append("应用日志目录不存在\n\n");
            }
            
            // 2. 添加系统日志
            logContent.append("\n").append("=".repeat(80)).append("\n");
            logContent.append("系统日志 (System Logs - wms-backend)\n");
            logContent.append("=".repeat(80)).append("\n\n");
            
            try {
                Process process = Runtime.getRuntime().exec(
                    new String[]{"journalctl", "-u", "wms-backend", "--since", "1 day ago", "-n", "1000"});
                
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    logContent.append(line).append("\n");
                }
                reader.close();
                
                System.out.println("已添加系统日志");
            } catch (Exception e) {
                logContent.append("无法获取系统日志: ").append(e.getMessage()).append("\n\n");
                System.err.println("无法获取systemd日志: " + e.getMessage());
            }
            
            // 3. 结束标记
            logContent.append("\n").append("=".repeat(80)).append("\n");
            logContent.append("日志导出完成\n");
            logContent.append("=".repeat(80)).append("\n");
            
            // 转换为字节数组
            byte[] logBytes = logContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("日志文件大小: " + logBytes.length + " 字节");
            
            if (logBytes.length == 0) {
                System.err.println("警告：日志文件为空！");
                return ResponseEntity.noContent().build();
            }
            
            // 使用ByteArrayResource返回
            org.springframework.core.io.ByteArrayResource resource = 
                new org.springframework.core.io.ByteArrayResource(logBytes);
            
            String filename = "system-logs-" + 
                java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .contentLength(logBytes.length)
                    .body(resource);
                    
        } catch (Exception e) {
            System.err.println("下载日志失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
    /**
     * 导出业务数据 (CSV格式)
     */
    @GetMapping("/export-data")
    public ResponseEntity<Resource> exportBusinessData(
            @RequestParam(required = false) String type) {
        try {
            StringBuilder csvContent = new StringBuilder();
            String filename = "business-data-export-" + 
                java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
            
            // 根据type参数决定导出哪些数据
            if (type == null || type.equals("all")) {
                // 导出所有业务数据
                csvContent.append("=".repeat(80)).append("\n");
                csvContent.append("业务数据完整导出 - ").append(java.time.LocalDateTime.now()).append("\n");
                csvContent.append("=".repeat(80)).append("\n\n");
                
                // 1. 计件记录
                appendPieceWorkData(csvContent);
                
                // 2. 库存数据
                appendInventoryData(csvContent);
                
                // 3. 毛坯库存
                appendBlankInventoryData(csvContent);
                
                // 4. 单价表
                appendPriceTableData(csvContent);
                
                // 5. 自动入库规则
                appendAutoStorageRulesData(csvContent);
                
                // 6. 组装规则
                appendAssemblyRulesData(csvContent);
                
            } else if (type.equals("piecework")) {
                appendPieceWorkData(csvContent);
            } else if (type.equals("inventory")) {
                appendInventoryData(csvContent);
            } else if (type.equals("blank")) {
                appendBlankInventoryData(csvContent);
            } else if (type.equals("price")) {
                appendPriceTableData(csvContent);
            }
            
            csvContent.append("\n").append("=".repeat(80)).append("\n");
            csvContent.append("导出完成\n");
            csvContent.append("=".repeat(80)).append("\n");
            
            // 转换为字节数组 (UTF-8 with BOM for Excel)
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] content = csvContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] csvBytes = new byte[bom.length + content.length];
            System.arraycopy(bom, 0, csvBytes, 0, bom.length);
            System.arraycopy(content, 0, csvBytes, bom.length, content.length);
            
            System.out.println("数据导出完成，大小: " + csvBytes.length + " 字节");
            
            org.springframework.core.io.ByteArrayResource resource = 
                new org.springframework.core.io.ByteArrayResource(csvBytes);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                    .contentLength(csvBytes.length)
                    .body(resource);
                    
        } catch (Exception e) {
            System.err.println("导出数据失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
    
    private void appendPieceWorkData(StringBuilder csv) {
        csv.append("\n【计件记录】\n");
        csv.append("ID,工人姓名,产品名称,数量,单价,总金额,半成品,录入时间,录入人\n");
        
        try {
            java.util.List<?> records = pieceWorkRepository.findAll();
            for (Object obj : records) {
                com.wms.entity.PieceWork pw = (com.wms.entity.PieceWork) obj;
                csv.append(pw.getId()).append(",")
                   .append(escapeCSV(pw.getWorkerName())).append(",")
                   .append(escapeCSV(pw.getProductName())).append(",")
                   .append(pw.getQuantity()).append(",")
                   .append(pw.getUnitPrice() != null ? pw.getUnitPrice() : "").append(",")
                   .append(pw.getTotalAmount() != null ? pw.getTotalAmount() : "").append(",")
                   .append(pw.getSemiFinished()).append(",")
                   .append(pw.getCreatedTime()).append(",")
                   .append(escapeCSV(pw.getCreatedBy())).append("\n");
            }
            csv.append("总计: ").append(records.size()).append(" 条记录\n\n");
        } catch (Exception e) {
            csv.append("导出失败: ").append(e.getMessage()).append("\n\n");
        }
    }
    
    private void appendInventoryData(StringBuilder csv) {
        csv.append("\n【库存管理】\n");
        csv.append("ID,产品名称,规格,数量,单位,材质,连接方式,单价,备注,最后更新时间\n");
        
        try {
            java.util.List<?> items = inventoryRepository.findAll();
            for (Object obj : items) {
                com.wms.entity.InventoryItem item = (com.wms.entity.InventoryItem) obj;
                csv.append(item.getId()).append(",")
                   .append(escapeCSV(item.getProductName())).append(",")
                   .append(escapeCSV(item.getSpecification())).append(",")
                   .append(item.getQuantity()).append(",")
                   .append(escapeCSV(item.getUnit())).append(",")
                   .append(escapeCSV(item.getMaterial())).append(",")
                   .append(escapeCSV(item.getConnectionType())).append(",")
                   .append(item.getUnitPrice() != null ? item.getUnitPrice() : "").append(",")
                   .append(escapeCSV(item.getRemarks())).append(",")
                   .append(item.getUpdatedTime()).append("\n");
            }
            csv.append("总计: ").append(items.size()).append(" 条记录\n\n");
        } catch (Exception e) {
            csv.append("导出失败: ").append(e.getMessage()).append("\n\n");
        }
    }
    
    private void appendBlankInventoryData(StringBuilder csv) {
        csv.append("\n【毛坯库存】\n");
        csv.append("ID,产品名称,当前数量,单位,最后更新时间\n");
        
        try {
            java.util.List<?> blanks = blankInventoryRepository.findAll();
            for (Object obj : blanks) {
                com.wms.entity.BlankInventory blank = (com.wms.entity.BlankInventory) obj;
                csv.append(blank.getId()).append(",")
                   .append(escapeCSV(blank.getProductName())).append(",")
                   .append(blank.getQuantity()).append(",")
                   .append(escapeCSV(blank.getUnit())).append(",")
                   .append(blank.getUpdatedAt()).append("\n");
            }
            csv.append("总计: ").append(blanks.size()).append(" 条记录\n\n");
        } catch (Exception e) {
            csv.append("导出失败: ").append(e.getMessage()).append("\n\n");
        }
    }
    
    private void appendAssemblyRulesData(StringBuilder csv) {
        csv.append("\n【组装规则】\n");
        csv.append("ID,规则名称,成品名称,组件名称,所需数量,创建时间\n");
        
        try {
            java.util.List<?> rules = assemblyRuleRepository.findAll();
            for (Object obj : rules) {
                com.wms.entity.AssemblyRule rule = (com.wms.entity.AssemblyRule) obj;
                // 遍历每个规则的items
                if (rule.getItems() != null && !rule.getItems().isEmpty()) {
                    for (com.wms.entity.AssemblyRuleItem item : rule.getItems()) {
                        csv.append(rule.getId()).append(",")
                           .append(escapeCSV(rule.getRuleName())).append(",")
                           .append(escapeCSV(rule.getProductName())).append(",")
                           .append(escapeCSV(item.getComponentName())).append(",")
                           .append(item.getQuantity()).append(",")
                           .append(rule.getCreatedAt()).append("\n");
                    }
                } else {
                    // 如果没有items，也显示规则信息
                    csv.append(rule.getId()).append(",")
                       .append(escapeCSV(rule.getRuleName())).append(",")
                       .append(escapeCSV(rule.getProductName())).append(",")
                       .append("(无组件)").append(",")
                       .append("0").append(",")
                       .append(rule.getCreatedAt()).append("\n");
                }
            }
            csv.append("总计: ").append(rules.size()).append(" 条规则\n\n");
        } catch (Exception e) {
            csv.append("导出失败: ").append(e.getMessage()).append("\n\n");
        }
    }
    private void appendPriceTableData(StringBuilder csv) {
        csv.append("\n【单价表】\n");
        csv.append("ID,产品名称,单价(元),是否启用,创建时间\n");
        
        try {
            java.util.List<?> prices = priceTableRepository.findAll();
            for (Object obj : prices) {
                com.wms.entity.PriceTable price = (com.wms.entity.PriceTable) obj;
                csv.append(price.getId()).append(",")
                   .append(escapeCSV(price.getProductName())).append(",")
                   .append(price.getUnitPrice()).append(",")
                   .append(price.getIsActive() ? "是" : "否").append(",")
                   .append(price.getCreatedAt()).append("\n");
            }
            csv.append("总计: ").append(prices.size()).append(" 条记录\n\n");
        } catch (Exception e) {
            csv.append("导出失败: ").append(e.getMessage()).append("\n\n");
        }
    }
    
    private void appendAutoStorageRulesData(StringBuilder csv) {
        csv.append("\n【自动入库规则】\n");
        csv.append("ID,产品模式,是否成品,毛坯产品名,消耗比例,优先级,是否启用\n");
        
        try {
            java.util.List<?> rules = autoStorageRuleRepository.findAll();
            for (Object obj : rules) {
                com.wms.entity.AutoStorageRule rule = (com.wms.entity.AutoStorageRule) obj;
                csv.append(rule.getId()).append(",")
                   .append(escapeCSV(rule.getProductPattern())).append(",")
                   .append(rule.getIsFinishedProduct() ? "是" : "否").append(",")
                   .append(escapeCSV(rule.getBlankProductName())).append(",")
                   .append(rule.getBlankQuantityPerUnit() != null ? rule.getBlankQuantityPerUnit() : "").append(",")
                   .append(rule.getPriority()).append(",")
                   .append(rule.getIsEnabled() ? "是" : "否").append("\n");
            }
            csv.append("总计: ").append(rules.size()).append(" 条记录\n\n");
        } catch (Exception e) {
            csv.append("导出失败: ").append(e.getMessage()).append("\n\n");
        }
    }
    
    
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    /**
     * 导出业务数据 (Excel格式)
     */
    @GetMapping("/export-data-excel")
    public ResponseEntity<Resource> exportBusinessDataExcel() {
        try {
            // 创建Excel工作簿
            org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            
            // 创建样式
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            headerStyle.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            
            // 1. 计件记录
            exportPieceWorkSheet(workbook, headerStyle);
            
            // 2. 库存管理
            exportInventorySheet(workbook, headerStyle);
            
            // 3. 毛坯库存
            exportBlankInventorySheet(workbook, headerStyle);
            
            // 4. 单价表
            exportPriceTableSheet(workbook, headerStyle);
            
            // 5. 自动入库规则
            exportAutoStorageRulesSheet(workbook, headerStyle);
            
            // 6. 组装规则
            exportAssemblyRulesSheet(workbook, headerStyle);
            
            // 写入到ByteArrayOutputStream
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            
            byte[] excelBytes = baos.toByteArray();
            System.out.println("Excel文件大小: " + excelBytes.length + " 字节");
            
            org.springframework.core.io.ByteArrayResource resource = 
                new org.springframework.core.io.ByteArrayResource(excelBytes);
            
            String filename = "business-data-export-" + 
                java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(excelBytes.length)
                    .body(resource);
                    
        } catch (Exception e) {
            System.err.println("导出Excel失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
    
    private void exportPieceWorkSheet(org.apache.poi.ss.usermodel.Workbook workbook, 
                                      org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("计件记录");
        
        // 创建标题行
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "工人姓名", "产品名称", "数量", "单价", "总金额", "半成品", "录入时间", "录入人"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 填充数据
        java.util.List<?> records = pieceWorkRepository.findAll();
        int rowNum = 1;
        for (Object obj : records) {
            com.wms.entity.PieceWork pw = (com.wms.entity.PieceWork) obj;
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(pw.getId());
            row.createCell(1).setCellValue(pw.getWorkerName() != null ? pw.getWorkerName() : "");
            row.createCell(2).setCellValue(pw.getProductName() != null ? pw.getProductName() : "");
            row.createCell(3).setCellValue(pw.getQuantity() != null ? pw.getQuantity() : 0);
            row.createCell(4).setCellValue(pw.getUnitPrice() != null ? pw.getUnitPrice().doubleValue() : 0);
            row.createCell(5).setCellValue(pw.getTotalAmount() != null ? pw.getTotalAmount().doubleValue() : 0);
            row.createCell(6).setCellValue(pw.getSemiFinished() != null ? pw.getSemiFinished() : "");
            row.createCell(7).setCellValue(pw.getCreatedTime() != null ? pw.getCreatedTime().toString() : "");
            row.createCell(8).setCellValue(pw.getCreatedBy() != null ? pw.getCreatedBy() : "");
        }
        
        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void exportInventorySheet(org.apache.poi.ss.usermodel.Workbook workbook, 
                                      org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("库存管理");
        
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "产品名称", "规格", "数量", "单位", "材质", "连接方式", "单价", "备注", "最后更新时间"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        java.util.List<?> items = inventoryRepository.findAll();
        int rowNum = 1;
        for (Object obj : items) {
            com.wms.entity.InventoryItem item = (com.wms.entity.InventoryItem) obj;
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(item.getId());
            row.createCell(1).setCellValue(item.getProductName() != null ? item.getProductName() : "");
            row.createCell(2).setCellValue(item.getSpecification() != null ? item.getSpecification() : "");
            row.createCell(3).setCellValue(item.getQuantity() != null ? item.getQuantity() : 0);
            row.createCell(4).setCellValue(item.getUnit() != null ? item.getUnit() : "");
            row.createCell(5).setCellValue(item.getMaterial() != null ? item.getMaterial() : "");
            row.createCell(6).setCellValue(item.getConnectionType() != null ? item.getConnectionType() : "");
            row.createCell(7).setCellValue(item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() : 0);
            row.createCell(8).setCellValue(item.getRemarks() != null ? item.getRemarks() : "");
            row.createCell(9).setCellValue(item.getUpdatedTime() != null ? item.getUpdatedTime().toString() : "");
        }
        
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void exportBlankInventorySheet(org.apache.poi.ss.usermodel.Workbook workbook, 
                                          org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("毛坯库存");
        
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "产品名称", "当前数量", "单位", "最后更新时间"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        java.util.List<?> blanks = blankInventoryRepository.findAll();
        int rowNum = 1;
        for (Object obj : blanks) {
            com.wms.entity.BlankInventory blank = (com.wms.entity.BlankInventory) obj;
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(blank.getId());
            row.createCell(1).setCellValue(blank.getProductName() != null ? blank.getProductName() : "");
            row.createCell(2).setCellValue(blank.getQuantity() != null ? blank.getQuantity() : 0);
            row.createCell(3).setCellValue(blank.getUnit() != null ? blank.getUnit() : "");
            row.createCell(4).setCellValue(blank.getUpdatedAt() != null ? blank.getUpdatedAt().toString() : "");
        }
        
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void exportPriceTableSheet(org.apache.poi.ss.usermodel.Workbook workbook, 
                                      org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("单价表");
        
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "产品名称", "单价(元)", "是否启用", "创建时间"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        java.util.List<?> prices = priceTableRepository.findAll();
        int rowNum = 1;
        for (Object obj : prices) {
            com.wms.entity.PriceTable price = (com.wms.entity.PriceTable) obj;
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(price.getId());
            row.createCell(1).setCellValue(price.getProductName() != null ? price.getProductName() : "");
            row.createCell(2).setCellValue(price.getUnitPrice() != null ? price.getUnitPrice().doubleValue() : 0);
            row.createCell(3).setCellValue(price.getIsActive() != null && price.getIsActive() ? "是" : "否");
            row.createCell(4).setCellValue(price.getCreatedAt() != null ? price.getCreatedAt().toString() : "");
        }
        
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void exportAutoStorageRulesSheet(org.apache.poi.ss.usermodel.Workbook workbook, 
                                            org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("自动入库规则");
        
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "产品模式", "是否成品", "毛坯产品名", "消耗比例", "优先级", "是否启用"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        java.util.List<?> rules = autoStorageRuleRepository.findAll();
        int rowNum = 1;
        for (Object obj : rules) {
            com.wms.entity.AutoStorageRule rule = (com.wms.entity.AutoStorageRule) obj;
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            
            row.createCell(0).setCellValue(rule.getId());
            row.createCell(1).setCellValue(rule.getProductPattern() != null ? rule.getProductPattern() : "");
            row.createCell(2).setCellValue(rule.getIsFinishedProduct() ? "是" : "否");
            row.createCell(3).setCellValue(rule.getBlankProductName() != null ? rule.getBlankProductName() : "");
            row.createCell(4).setCellValue(rule.getBlankQuantityPerUnit() != null ? rule.getBlankQuantityPerUnit().doubleValue() : 0);
            row.createCell(5).setCellValue(rule.getPriority() != null ? rule.getPriority() : 0);
            row.createCell(6).setCellValue(rule.getIsEnabled() ? "是" : "否");
        }
        
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void exportAssemblyRulesSheet(org.apache.poi.ss.usermodel.Workbook workbook, 
                                         org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("组装规则");
        
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "规则名称", "成品名称", "组件名称", "所需数量", "创建时间"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        java.util.List<?> rules = assemblyRuleRepository.findAll();
        int rowNum = 1;
        for (Object obj : rules) {
            com.wms.entity.AssemblyRule rule = (com.wms.entity.AssemblyRule) obj;
            if (rule.getItems() != null && !rule.getItems().isEmpty()) {
                for (com.wms.entity.AssemblyRuleItem item : rule.getItems()) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(rule.getId());
                    row.createCell(1).setCellValue(rule.getRuleName() != null ? rule.getRuleName() : "");
                    row.createCell(2).setCellValue(rule.getProductName() != null ? rule.getProductName() : "");
                    row.createCell(3).setCellValue(item.getComponentName() != null ? item.getComponentName() : "");
                    row.createCell(4).setCellValue(item.getQuantity() != null ? item.getQuantity() : 0);
                    row.createCell(5).setCellValue(rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : "");
                }
            }
        }
        
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
