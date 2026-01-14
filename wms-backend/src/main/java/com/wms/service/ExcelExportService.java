package com.wms.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel导出服务
 */
@Service
public class ExcelExportService {

    /**
     * 导出计件记录为Excel
     */
    public ByteArrayOutputStream exportPieceworkRecords(List<Map<String, Object>> records, 
                                                       Map<String, Object> filters, 
                                                       Map<String, Object> summary) throws IOException {
        
        Workbook workbook = new XSSFWorkbook();
        
        // 创建样式
        Map<String, CellStyle> styles = createStyles(workbook);
        
        // 创建主数据表
        createDataSheet(workbook, records, filters, summary, styles);
        
        // 创建统计表
        createSummarySheet(workbook, records, styles);
        
        // 输出到字节流
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream;
    }

    /**
     * 导出详细报表
     */
    public ByteArrayOutputStream exportDetailedReport(List<Map<String, Object>> records, 
                                                     Map<String, Object> filters,
                                                     boolean includeStatistics, 
                                                     boolean includeCharts) throws IOException {
        
        Workbook workbook = new XSSFWorkbook();
        
        // 创建样式
        Map<String, CellStyle> styles = createStyles(workbook);
        
        // 创建主数据表
        createDataSheet(workbook, records, filters, null, styles);
        
        if (includeStatistics) {
            // 创建统计表
            createSummarySheet(workbook, records, styles);
            
            // 创建工人统计表
            createWorkerStatisticsSheet(workbook, records, styles);
            
            // 创建产品统计表
            createProductStatisticsSheet(workbook, records, styles);
            
            // 创建月度统计表
            createMonthlyStatisticsSheet(workbook, records, styles);
        }
        
        // 输出到字节流
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream;
    }

    /**
     * 创建样式
     */
    private Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();
        
        // 标题样式
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("title", titleStyle);
        
        // 表头样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        styles.put("header", headerStyle);
        
        // 数据样式
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setAlignment(HorizontalAlignment.CENTER);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        styles.put("data", dataStyle);
        
        // 数字样式
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.cloneStyleFrom(dataStyle);
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        styles.put("number", numberStyle);
        
        // 整数样式
        CellStyle integerStyle = workbook.createCellStyle();
        integerStyle.cloneStyleFrom(dataStyle);
        integerStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        styles.put("integer", integerStyle);
        
        return styles;
    }

    /**
     * 创建数据表
     */
    private void createDataSheet(Workbook workbook, List<Map<String, Object>> records, 
                                Map<String, Object> filters, Map<String, Object> summary,
                                Map<String, CellStyle> styles) {
        
        Sheet sheet = workbook.createSheet("计件记录");
        
        int rowNum = 0;
        
        // 创建标题
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("计件记录导出报表");
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));
        
        // 空行
        rowNum++;
        
        // 创建筛选条件信息
        if (filters != null) {
            Row filterTitleRow = sheet.createRow(rowNum++);
            Cell filterTitleCell = filterTitleRow.createCell(0);
            filterTitleCell.setCellValue("筛选条件：");
            filterTitleCell.setCellStyle(styles.get("header"));
            
            if (filters.get("worker") != null && !filters.get("worker").toString().isEmpty()) {
                Row filterRow = sheet.createRow(rowNum++);
                filterRow.createCell(0).setCellValue("工人姓名：" + filters.get("worker"));
            }
            if (filters.get("product") != null && !filters.get("product").toString().isEmpty()) {
                Row filterRow = sheet.createRow(rowNum++);
                filterRow.createCell(0).setCellValue("产品名称：" + filters.get("product"));
            }
            if (filters.get("startDate") != null && filters.get("endDate") != null) {
                Row filterRow = sheet.createRow(rowNum++);
                filterRow.createCell(0).setCellValue("日期范围：" + filters.get("startDate") + " 至 " + filters.get("endDate"));
            }
            
            // 空行
            rowNum++;
        }
        
        // 创建汇总信息
        if (summary != null) {
            Row summaryTitleRow = sheet.createRow(rowNum++);
            Cell summaryTitleCell = summaryTitleRow.createCell(0);
            summaryTitleCell.setCellValue("汇总信息：");
            summaryTitleCell.setCellStyle(styles.get("header"));
            
            Row summaryRow = sheet.createRow(rowNum++);
            summaryRow.createCell(0).setCellValue("总记录数：" + summary.get("totalRecords"));
            summaryRow.createCell(2).setCellValue("总数量：" + summary.get("totalQuantity"));
            summaryRow.createCell(4).setCellValue("总金额：¥" + summary.get("totalAmount"));
            
            // 空行
            rowNum++;
        }
        
        // 创建表头
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"序号", "工人姓名", "产品名称", "规格", "材质", "数量", "单位", "单价", "总金额", "工作日期", "是否半成品", "报废数量"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }
        
        // 填充数据
        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            Row dataRow = sheet.createRow(rowNum++);
            
            // 序号
            Cell cell0 = dataRow.createCell(0);
            cell0.setCellValue(i + 1);
            cell0.setCellStyle(styles.get("integer"));
            
            // 工人姓名
            Cell cell1 = dataRow.createCell(1);
            cell1.setCellValue(getStringValue(record, "workerName"));
            cell1.setCellStyle(styles.get("data"));
            
            // 产品名称
            Cell cell2 = dataRow.createCell(2);
            cell2.setCellValue(getStringValue(record, "productName"));
            cell2.setCellStyle(styles.get("data"));
            
            // 规格
            Cell cell3 = dataRow.createCell(3);
            cell3.setCellValue(getStringValue(record, "specification"));
            cell3.setCellStyle(styles.get("data"));
            
            // 材质
            Cell cell4 = dataRow.createCell(4);
            cell4.setCellValue(getStringValue(record, "material"));
            cell4.setCellStyle(styles.get("data"));
            
            // 数量
            Cell cell5 = dataRow.createCell(5);
            cell5.setCellValue(getIntValue(record, "quantity"));
            cell5.setCellStyle(styles.get("integer"));
            
            // 单位
            Cell cell6 = dataRow.createCell(6);
            cell6.setCellValue(getStringValue(record, "unit", "个"));
            cell6.setCellStyle(styles.get("data"));
            
            // 单价
            Cell cell7 = dataRow.createCell(7);
            cell7.setCellValue(getDoubleValue(record, "unitPrice"));
            cell7.setCellStyle(styles.get("number"));
            
            // 总金额
            Cell cell8 = dataRow.createCell(8);
            cell8.setCellValue(getDoubleValue(record, "totalAmount"));
            cell8.setCellStyle(styles.get("number"));
            
            // 工作日期
            Cell cell9 = dataRow.createCell(9);
            String workDate = getStringValue(record, "workDate");
            if (workDate.length() > 10) {
                workDate = workDate.substring(0, 10);
            }
            cell9.setCellValue(workDate);
            cell9.setCellStyle(styles.get("data"));
            
            // 是否半成品
            Cell cell10 = dataRow.createCell(10);
            cell10.setCellValue(getStringValue(record, "semiFinished", "否"));
            cell10.setCellStyle(styles.get("data"));
            
            // 报废数量
            Cell cell11 = dataRow.createCell(11);
            cell11.setCellValue(getIntValue(record, "defectQuantity"));
            cell11.setCellStyle(styles.get("integer"));
        }
        
        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            // 设置最小宽度
            if (sheet.getColumnWidth(i) < 2000) {
                sheet.setColumnWidth(i, 2000);
            }
            // 设置最大宽度
            if (sheet.getColumnWidth(i) > 8000) {
                sheet.setColumnWidth(i, 8000);
            }
        }
    }

    /**
     * 创建汇总统计表
     */
    private void createSummarySheet(Workbook workbook, List<Map<String, Object>> records, 
                                   Map<String, CellStyle> styles) {
        
        Sheet sheet = workbook.createSheet("汇总统计");
        
        int rowNum = 0;
        
        // 标题
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("汇总统计报表");
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        
        rowNum++; // 空行
        
        // 基本统计
        Row basicStatsTitle = sheet.createRow(rowNum++);
        basicStatsTitle.createCell(0).setCellValue("基本统计");
        basicStatsTitle.getCell(0).setCellStyle(styles.get("header"));
        
        // 计算统计数据
        int totalRecords = records.size();
        int totalQuantity = records.stream().mapToInt(r -> getIntValue(r, "quantity")).sum();
        double totalAmount = records.stream().mapToDouble(r -> getDoubleValue(r, "totalAmount")).sum();
        int defectRecords = (int) records.stream().filter(r -> getIntValue(r, "defectQuantity") > 0).count();
        int semiFinishedRecords = (int) records.stream().filter(r -> "是".equals(getStringValue(r, "semiFinished"))).count();
        
        Row statsRow1 = sheet.createRow(rowNum++);
        statsRow1.createCell(0).setCellValue("总记录数");
        Cell cell1 = statsRow1.createCell(1);
        cell1.setCellValue(totalRecords);
        cell1.setCellStyle(styles.get("integer"));
        
        Row statsRow2 = sheet.createRow(rowNum++);
        statsRow2.createCell(0).setCellValue("总数量");
        Cell cell2 = statsRow2.createCell(1);
        cell2.setCellValue(totalQuantity);
        cell2.setCellStyle(styles.get("integer"));
        
        Row statsRow3 = sheet.createRow(rowNum++);
        statsRow3.createCell(0).setCellValue("总金额");
        Cell cell3 = statsRow3.createCell(1);
        cell3.setCellValue(totalAmount);
        cell3.setCellStyle(styles.get("number"));
        
        Row statsRow4 = sheet.createRow(rowNum++);
        statsRow4.createCell(0).setCellValue("报废记录数");
        Cell cell4 = statsRow4.createCell(1);
        cell4.setCellValue(defectRecords);
        cell4.setCellStyle(styles.get("integer"));
        
        Row statsRow5 = sheet.createRow(rowNum++);
        statsRow5.createCell(0).setCellValue("半成品记录数");
        Cell cell5 = statsRow5.createCell(1);
        cell5.setCellValue(semiFinishedRecords);
        cell5.setCellStyle(styles.get("integer"));
        
        // 自动调整列宽
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    /**
     * 创建工人统计表
     */
    private void createWorkerStatisticsSheet(Workbook workbook, List<Map<String, Object>> records, 
                                           Map<String, CellStyle> styles) {
        
        Sheet sheet = workbook.createSheet("工人统计");
        
        // 按工人分组统计
        Map<String, List<Map<String, Object>>> workerGroups = records.stream()
            .collect(Collectors.groupingBy(r -> getStringValue(r, "workerName", "未知")));
        
        int rowNum = 0;
        
        // 标题
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("工人统计报表");
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        
        rowNum++; // 空行
        
        // 表头
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"工人姓名", "记录数", "总数量", "总金额", "平均单价"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }
        
        // 数据行
        for (Map.Entry<String, List<Map<String, Object>>> entry : workerGroups.entrySet()) {
            String workerName = entry.getKey();
            List<Map<String, Object>> workerRecords = entry.getValue();
            
            int recordCount = workerRecords.size();
            int totalQuantity = workerRecords.stream().mapToInt(r -> getIntValue(r, "quantity")).sum();
            double totalAmount = workerRecords.stream().mapToDouble(r -> getDoubleValue(r, "totalAmount")).sum();
            double avgPrice = totalQuantity > 0 ? totalAmount / totalQuantity : 0;
            
            Row dataRow = sheet.createRow(rowNum++);
            
            dataRow.createCell(0).setCellValue(workerName);
            dataRow.getCell(0).setCellStyle(styles.get("data"));
            
            Cell cell1 = dataRow.createCell(1);
            cell1.setCellValue(recordCount);
            cell1.setCellStyle(styles.get("integer"));
            
            Cell cell2 = dataRow.createCell(2);
            cell2.setCellValue(totalQuantity);
            cell2.setCellStyle(styles.get("integer"));
            
            Cell cell3 = dataRow.createCell(3);
            cell3.setCellValue(totalAmount);
            cell3.setCellStyle(styles.get("number"));
            
            Cell cell4 = dataRow.createCell(4);
            cell4.setCellValue(avgPrice);
            cell4.setCellStyle(styles.get("number"));
        }
        
        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 创建产品统计表
     */
    private void createProductStatisticsSheet(Workbook workbook, List<Map<String, Object>> records, 
                                            Map<String, CellStyle> styles) {
        
        Sheet sheet = workbook.createSheet("产品统计");
        
        // 按产品分组统计
        Map<String, List<Map<String, Object>>> productGroups = records.stream()
            .collect(Collectors.groupingBy(r -> getStringValue(r, "productName", "未知")));
        
        int rowNum = 0;
        
        // 标题
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("产品统计报表");
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        
        rowNum++; // 空行
        
        // 表头
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"产品名称", "记录数", "总数量", "总金额", "平均单价"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }
        
        // 数据行
        for (Map.Entry<String, List<Map<String, Object>>> entry : productGroups.entrySet()) {
            String productName = entry.getKey();
            List<Map<String, Object>> productRecords = entry.getValue();
            
            int recordCount = productRecords.size();
            int totalQuantity = productRecords.stream().mapToInt(r -> getIntValue(r, "quantity")).sum();
            double totalAmount = productRecords.stream().mapToDouble(r -> getDoubleValue(r, "totalAmount")).sum();
            double avgPrice = totalQuantity > 0 ? totalAmount / totalQuantity : 0;
            
            Row dataRow = sheet.createRow(rowNum++);
            
            dataRow.createCell(0).setCellValue(productName);
            dataRow.getCell(0).setCellStyle(styles.get("data"));
            
            Cell cell1 = dataRow.createCell(1);
            cell1.setCellValue(recordCount);
            cell1.setCellStyle(styles.get("integer"));
            
            Cell cell2 = dataRow.createCell(2);
            cell2.setCellValue(totalQuantity);
            cell2.setCellStyle(styles.get("integer"));
            
            Cell cell3 = dataRow.createCell(3);
            cell3.setCellValue(totalAmount);
            cell3.setCellStyle(styles.get("number"));
            
            Cell cell4 = dataRow.createCell(4);
            cell4.setCellValue(avgPrice);
            cell4.setCellStyle(styles.get("number"));
        }
        
        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 创建月度统计表
     */
    private void createMonthlyStatisticsSheet(Workbook workbook, List<Map<String, Object>> records, 
                                            Map<String, CellStyle> styles) {
        
        Sheet sheet = workbook.createSheet("月度统计");
        
        // 按月份分组统计
        Map<String, List<Map<String, Object>>> monthlyGroups = records.stream()
            .collect(Collectors.groupingBy(r -> {
                String workDate = getStringValue(r, "workDate");
                if (workDate.length() >= 7) {
                    return workDate.substring(0, 7); // YYYY-MM
                }
                return "未知";
            }));
        
        int rowNum = 0;
        
        // 标题
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("月度统计报表");
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        
        rowNum++; // 空行
        
        // 表头
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"月份", "记录数", "总数量", "总金额", "平均日产量"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }
        
        // 数据行（按月份排序）
        List<String> sortedMonths = monthlyGroups.keySet().stream().sorted().collect(Collectors.toList());
        
        for (String month : sortedMonths) {
            List<Map<String, Object>> monthlyRecords = monthlyGroups.get(month);
            
            int recordCount = monthlyRecords.size();
            int totalQuantity = monthlyRecords.stream().mapToInt(r -> getIntValue(r, "quantity")).sum();
            double totalAmount = monthlyRecords.stream().mapToDouble(r -> getDoubleValue(r, "totalAmount")).sum();
            
            // 计算该月的工作天数
            Set<String> workDays = monthlyRecords.stream()
                .map(r -> getStringValue(r, "workDate").substring(0, 10))
                .collect(Collectors.toSet());
            double avgDailyQuantity = workDays.size() > 0 ? (double) totalQuantity / workDays.size() : 0;
            
            Row dataRow = sheet.createRow(rowNum++);
            
            dataRow.createCell(0).setCellValue(month);
            dataRow.getCell(0).setCellStyle(styles.get("data"));
            
            Cell cell1 = dataRow.createCell(1);
            cell1.setCellValue(recordCount);
            cell1.setCellStyle(styles.get("integer"));
            
            Cell cell2 = dataRow.createCell(2);
            cell2.setCellValue(totalQuantity);
            cell2.setCellStyle(styles.get("integer"));
            
            Cell cell3 = dataRow.createCell(3);
            cell3.setCellValue(totalAmount);
            cell3.setCellStyle(styles.get("number"));
            
            Cell cell4 = dataRow.createCell(4);
            cell4.setCellValue(avgDailyQuantity);
            cell4.setCellStyle(styles.get("number"));
        }
        
        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // 辅助方法
    private String getStringValue(Map<String, Object> map, String key) {
        return getStringValue(map, key, "");
    }
    
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}