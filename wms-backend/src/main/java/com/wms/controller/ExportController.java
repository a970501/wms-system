package com.wms.controller;

import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/export")
public class ExportController {
    
    @Autowired
    private InventoryItemRepository inventoryRepository;
    
    @Autowired
    private PieceWorkRepository pieceWorkRepository;
    
    @Autowired
    private PriceTableRepository priceTableRepository;
    
    @Autowired
    private AutoStorageRuleRepository autoStorageRuleRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 导出库存数据为CSV
     */
    @GetMapping("/inventory/csv")
    public void exportInventoryToCsv(HttpServletResponse response) throws IOException {
        List<InventoryItem> items = inventoryRepository.findAll();
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=inventory_" + System.currentTimeMillis() + ".csv");
        
        PrintWriter writer = response.getWriter();
        
        // 写入BOM以支持Excel正确显示中文
        writer.write('\ufeff');
        
        // 写入表头
        writer.println("产品名称,规格,数量,材料,单位,单价,备注");
        
        // 写入数据
        for (InventoryItem item : items) {
            writer.printf("%s,%s,%d,%s,%s,%.2f,%s%n",
                escapeCsv(item.getProductName()),
                escapeCsv(item.getSpecification()),
                item.getQuantity(),
                escapeCsv(item.getMaterial()),
                escapeCsv(item.getUnit()),
                item.getUnitPrice(),
                escapeCsv(item.getRemarks())
            );
        }
        
        writer.flush();
    }
    
    /**
     * 导出计件数据为CSV
     */
    @GetMapping("/piecework/csv")
    public void exportPieceWorkToCsv(HttpServletResponse response) throws IOException {
        List<PieceWork> works = pieceWorkRepository.findAll();
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=piecework_" + System.currentTimeMillis() + ".csv");
        
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        
        writer.println("工人姓名,产品名称,数量,材料,单价,总金额,创建时间");

        for (PieceWork work : works) {
            writer.printf("%s,%s,%d,%s,%.2f,%.2f,%s%n",
                escapeCsv(work.getWorkerName()),
                escapeCsv(work.getProductName()),
                work.getQuantity(),
                escapeCsv(work.getMaterial()),
                work.getUnitPrice(),
                work.getTotalAmount(),
                work.getCreatedTime() != null ? work.getCreatedTime().format(formatter) : ""
            );
        }
        
        writer.flush();
    }
    
    /**
     * 导出单价表为CSV
     */
    @GetMapping("/price-table/csv")
    public void exportPriceTableToCsv(HttpServletResponse response) throws IOException {
        List<PriceTable> prices = priceTableRepository.findAll();
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=price_table_" + System.currentTimeMillis() + ".csv");
        
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        
        writer.println("产品名称,材料,规格,单价,单位,状态,生效日期,备注");

        for (PriceTable price : prices) {
            writer.printf("%s,%s,%s,%.2f,%s,%s,%s,%s%n",
                escapeCsv(price.getProductName()),
                escapeCsv(price.getMaterial()),
                escapeCsv(price.getSpecification()),
                price.getUnitPrice(),
                escapeCsv(price.getUnit()),
                price.getIsActive() ? "启用" : "禁用",
                price.getEffectiveDate() != null ? price.getEffectiveDate().toString() : "",
                escapeCsv(price.getRemarks())
            );
        }
        
        writer.flush();
    }
    
    /**
     * 导出用户数据为CSV
     */
    @GetMapping("/users/csv")
    public void exportUsersToCsv(HttpServletResponse response) throws IOException {
        List<User> users = userRepository.findAll();
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=users_" + System.currentTimeMillis() + ".csv");
        
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        
        writer.println("用户名,真实姓名,邮箱,手机号,角色,状态,最后登录,创建时间");
        
        for (User user : users) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                escapeCsv(user.getUsername()),
                escapeCsv(user.getRealName()),
                escapeCsv(user.getEmail()),
                escapeCsv(user.getPhone()),
                getRoleName(user.getRole()),
                user.getStatus().equals("active") ? "活跃" : "禁用",
                user.getLastLogin() != null ? user.getLastLogin().format(formatter) : "从未登录",
                user.getCreatedAt() != null ? user.getCreatedAt().format(formatter) : ""
            );
        }
        
        writer.flush();
    }
    
    /**
     * 导出自动入库规则为CSV
     */
    @GetMapping("/auto-storage-rules/csv")
    public void exportAutoStorageRulesToCsv(HttpServletResponse response) throws IOException {
        List<AutoStorageRule> rules = autoStorageRuleRepository.findAll();
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=auto_storage_rules_" + System.currentTimeMillis() + ".csv");
        
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        
        writer.println("规则名称,产品模式,目标位置,优先级,触发条件,状态,描述");
        
        for (AutoStorageRule rule : rules) {
            writer.printf("%s,%s,%s,%d,%s,%s,%s%n",
                escapeCsv(rule.getRuleName()),
                escapeCsv(rule.getProductPattern()),
                escapeCsv(rule.getTargetLocation()),
                rule.getPriority(),
                escapeCsv(rule.getTriggerCondition()),
                rule.getIsEnabled() ? "启用" : "禁用",
                escapeCsv(rule.getDescription())
            );
        }
        
        writer.flush();
    }
    
    /**
     * 导出所有数据（打包）
     */
    @GetMapping("/all/csv")
    public void exportAllToCsv(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=smartstock_export_" + System.currentTimeMillis() + ".zip");
        
        // 简化版本：只导出一个汇总文件
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=smartstock_summary_" + System.currentTimeMillis() + ".csv");
        
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        
        writer.println("数据类型,数量");
        writer.printf("库存品种,%d%n", inventoryRepository.count());
        writer.printf("计件记录,%d%n", pieceWorkRepository.count());
        writer.printf("单价记录,%d%n", priceTableRepository.count());
        writer.printf("自动入库规则,%d%n", autoStorageRuleRepository.count());
        writer.printf("用户数量,%d%n", userRepository.count());
        
        writer.flush();
    }
    
    /**
     * CSV转义
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * 获取角色名称
     */
    private String getRoleName(String role) {
        switch (role) {
            case "admin": return "管理员";
            case "warehouse_manager": return "仓库管理员";
            case "operator": return "操作员";
            case "viewer": return "查看者";
            default: return role;
        }
    }
}
