package com.wms.controller;

import com.wms.annotation.RequireAuth;
import com.wms.service.ExcelExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Excel导出控制器
 */
@RestController
@RequestMapping("/export")
@CrossOrigin(origins = "*")
public class ExcelExportController {

    @Autowired
    private ExcelExportService excelExportService;

    // 文件存储目录
    private static final String EXPORT_DIR = "/opt/app/wms/exports/";
    
    static {
        // 确保导出目录存在
        File dir = new File(EXPORT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 导出计件查询结果为Excel（生成下载链接）
     */
    @RequireAuth
    @PostMapping("/piecework-excel")
    public ResponseEntity<Map<String, Object>> exportPieceworkToExcel(@RequestBody Map<String, Object> requestData) {
        try {
            // 提取数据
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) requestData.get("records");
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) requestData.get("filters");
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) requestData.get("summary");

            // 生成Excel文件
            ByteArrayOutputStream outputStream = excelExportService.exportPieceworkRecords(records, filters, summary);
            
            // 生成唯一文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String filename = "计件查询结果_" + timestamp + "_" + uniqueId + ".xlsx";
            
            // 保存文件到服务器
            File file = new File(EXPORT_DIR + filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(outputStream.toByteArray());
            }
            
            // 生成下载链接
            String downloadUrl = "/api/export/download/" + filename;
            
            // 返回下载信息
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filename", "计件查询结果_" + timestamp + ".xlsx");
            response.put("downloadUrl", downloadUrl);
            response.put("fileSize", outputStream.size());
            response.put("message", "Excel文件生成成功");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "生成Excel文件失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 导出计件详细报表为Excel（生成下载链接）
     */
    @RequireAuth
    @PostMapping("/piecework-detailed-report")
    public ResponseEntity<Map<String, Object>> exportDetailedReport(@RequestBody Map<String, Object> requestData) {
        try {
            // 提取数据
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) requestData.get("records");
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) requestData.get("filters");
            boolean includeStatistics = Boolean.TRUE.equals(requestData.get("includeStatistics"));
            boolean includeCharts = Boolean.TRUE.equals(requestData.get("includeCharts"));

            // 生成详细报表
            ByteArrayOutputStream outputStream = excelExportService.exportDetailedReport(
                records, filters, includeStatistics, includeCharts);
            
            // 生成唯一文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String filename = "计件详细报表_" + timestamp + "_" + uniqueId + ".xlsx";
            
            // 保存文件到服务器
            File file = new File(EXPORT_DIR + filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(outputStream.toByteArray());
            }
            
            // 生成下载链接
            String downloadUrl = "/api/export/download/" + filename;
            
            // 返回下载信息
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filename", "计件详细报表_" + timestamp + ".xlsx");
            response.put("downloadUrl", downloadUrl);
            response.put("fileSize", outputStream.size());
            response.put("message", "详细报表生成成功");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "生成详细报表失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 下载Excel文件
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename) {
        try {
            // 安全检查：只允许下载xlsx文件
            if (!filename.endsWith(".xlsx") || filename.contains("..") || filename.contains("/")) {
                return ResponseEntity.badRequest().build();
            }
            
            File file = new File(EXPORT_DIR + filename);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            // 读取文件内容
            byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            // 从文件名中提取显示名称（去掉UUID部分）
            String displayName = filename.replaceAll("_[a-f0-9]{8}\\.xlsx$", ".xlsx");
            String encodedFilename = URLEncoder.encode(displayName, StandardCharsets.UTF_8.toString());
            headers.setContentDispositionFormData("attachment", encodedFilename);
            headers.add("Access-Control-Expose-Headers", "Content-Disposition");
            
            // 文件下载后删除（可选，也可以定期清理）
            // file.delete();
            
            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}