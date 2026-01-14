package com.wms.service;

import com.wms.entity.*;
import com.wms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import javax.persistence.criteria.Predicate;

@Service
public class PieceWorkService {

    private static final Logger log = LoggerFactory.getLogger(PieceWorkService.class);

    @Autowired
    private PieceWorkRepository pieceWorkRepository;

    @Autowired
    private InventoryItemRepository inventoryRepository;

    @Autowired
    private AutoStorageRuleRepository ruleRepository;

    @Autowired
    private PriceTableRepository priceTableRepository;

    @Autowired
    private BlankInventoryRepository blankInventoryRepository;

    @Autowired
    private InventoryLogRepository inventoryLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public PieceWork create(PieceWork pieceWork, String userRole) {
        System.out.println("==========================================");
        System.out.println("Piecework create - userRole: [" + userRole + "]");
        System.out.println("Worker: " + pieceWork.getWorkerName());
        System.out.println("Product: " + pieceWork.getProductName());
        System.out.println("Quantity: " + pieceWork.getQuantity());
        System.out.println("Defect Qty: " + pieceWork.getDefectQuantity());
        System.out.println("Semi-finished: " + pieceWork.getSemiFinished());
        System.out.println("==========================================");

        // 1. Auto fill unit price
        if (pieceWork.getUnitPrice() == null) {
            List<PriceTable> prices = priceTableRepository.findByProductNameAndIsActive(
                pieceWork.getProductName(), true);
            if (!prices.isEmpty()) {
                pieceWork.setUnitPrice(prices.get(0).getUnitPrice());
            }
        }

        // 2. Save piecework record
        PieceWork saved = pieceWorkRepository.save(pieceWork);

        // 3. Admin skips auto inventory
        if ("ADMIN".equals(userRole)) {
            System.out.println(">>> Admin, skip auto inventory");
            return saved;
        }

        // 4. Only semi-finished products auto inventory
        if (!"是".equals(pieceWork.getSemiFinished())) {
            System.out.println(">>> Not semi-finished, skip auto inventory");
            return saved;
        }

        System.out.println(">>> Normal user + semi-finished, execute auto inventory");

        // 5. Find matching auto storage rule
        AutoStorageRule rule = findMatchingRule(pieceWork.getProductName());

        if (rule != null && rule.getIsEnabled()) {
            System.out.println("=== Matched rule: " + rule.getRuleName() + " ===");

            int quantity = pieceWork.getQuantity();
            int defectQty = pieceWork.getDefectQuantity() != null ? pieceWork.getDefectQuantity() : 0;

            // 6. Consume blank (total = good + defect)
            if (Boolean.TRUE.equals(rule.getIsFinishedProduct())
                && rule.getBlankProductName() != null
                && !rule.getBlankProductName().trim().isEmpty()) {
                int totalConsumption = quantity + defectQty;
                consumeBlank(rule, pieceWork, totalConsumption);
            }

            // 7. Add to inventory (only good products)
            // Apply storage ratio: calculate actual inventory quantity
            int inventoryQuantity = calculateInventoryQuantity(quantity, rule.getStorageRatio());
            double factor = getStorageRatioFactor(rule.getStorageRatio());
            System.out.println(">>> Storage ratio: " + rule.getStorageRatio() + ", original qty: " + quantity + ", inventory qty: " + inventoryQuantity);
            createOrUpdateInventory(rule.getTargetLocation(), pieceWork, inventoryQuantity);

            // 8. Record inventory log for parts
            saveInventoryLog(saved.getId(), rule.getId(), "parts", rule.getTargetLocation(),
                pieceWork.getSpecification(), pieceWork.getMaterial(), quantity, inventoryQuantity, factor);

            // 9. Record inventory log for blank consumption
            if (Boolean.TRUE.equals(rule.getIsFinishedProduct())
                && rule.getBlankProductName() != null
                && !rule.getBlankProductName().trim().isEmpty()) {
                int blankConsumed = (quantity + defectQty) * rule.getBlankQuantityPerUnit();
                saveInventoryLog(saved.getId(), rule.getId(), "blank", rule.getBlankProductName(),
                    pieceWork.getSpecification(), pieceWork.getMaterial(), quantity + defectQty, -blankConsumed, 1.0);
            }
        } else {
            System.out.println("No matching rule for: " + pieceWork.getProductName());
        }

        return saved;
    }

    private void saveInventoryLog(Long pieceworkId, Long ruleId, String type, String productName,
                                   String spec, String material, int originalQty, int changeQty, double factor) {
        InventoryLog log = new InventoryLog();
        log.setPieceworkId(pieceworkId);
        log.setRuleId(ruleId);
        log.setInventoryType(type);
        log.setProductName(productName);
        log.setSpecification(spec);
        log.setMaterial(material);
        log.setOriginalQuantity(originalQty);
        log.setQuantityChange(changeQty);
        log.setCalculationFactor(factor);
        inventoryLogRepository.save(log);
        System.out.println("Saved inventory log: " + type + " " + productName + " change=" + changeQty);
    }

    @Transactional
    public PieceWork update(PieceWork pieceWork, String userRole) {
        System.out.println("==========================================");
        System.out.println("Update piecework - ID: " + pieceWork.getId());
        System.out.println("Worker: " + pieceWork.getWorkerName());
        System.out.println("Product: " + pieceWork.getProductName());
        System.out.println("Quantity: " + pieceWork.getQuantity());
        System.out.println("==========================================");

        // Get original record
        PieceWork original = pieceWorkRepository.findById(pieceWork.getId())
            .orElseThrow(() -> new RuntimeException("计件记录不存在"));

        // Update only submitted fields, keep original values for others
        if (pieceWork.getProductName() != null) {
            original.setProductName(pieceWork.getProductName());
        }
        if (pieceWork.getSpecification() != null) {
            original.setSpecification(pieceWork.getSpecification());
        }
        if (pieceWork.getMaterial() != null) {
            original.setMaterial(pieceWork.getMaterial());
        }
        if (pieceWork.getConnectionType() != null) {
            original.setConnectionType(pieceWork.getConnectionType());
        }
        if (pieceWork.getQuantity() != null) {
            original.setQuantity(pieceWork.getQuantity());
        }
        if (pieceWork.getUnitPrice() != null) {
            original.setUnitPrice(pieceWork.getUnitPrice());
        }
        if (pieceWork.getUnit() != null) {
            original.setUnit(pieceWork.getUnit());
        }
        if (pieceWork.getWorkerName() != null) {
            original.setWorkerName(pieceWork.getWorkerName());
        }
        if (pieceWork.getSemiFinished() != null) {
            original.setSemiFinished(pieceWork.getSemiFinished());
        }
        if (pieceWork.getIsDefective() != null) {
            original.setIsDefective(pieceWork.getIsDefective());
        }
        if (pieceWork.getDefectQuantity() != null) {
            original.setDefectQuantity(pieceWork.getDefectQuantity());
        }
        if (pieceWork.getDefectiveReason() != null) {
            original.setDefectiveReason(pieceWork.getDefectiveReason());
        }
        if (pieceWork.getRemarks() != null) {
            original.setRemarks(pieceWork.getRemarks());
        }

        // Calculate total amount
        if (original.getUnitPrice() != null && original.getQuantity() != null) {
            original.setTotalAmount(original.getUnitPrice().multiply(BigDecimal.valueOf(original.getQuantity())));
        }

        // Save updated record
        PieceWork updated = pieceWorkRepository.save(original);
        System.out.println(">>> Piecework updated successfully");
        return updated;
    }

    @Transactional
    public void deleteWithRollback(Long id, String userRole) {
        PieceWork pieceWork = pieceWorkRepository.findById(id).orElse(null);
        if (pieceWork == null) {
            System.out.println("Piecework not found: " + id);
            return;
        }

        System.out.println("==========================================");
        System.out.println("Delete piecework and rollback - ID: " + id);
        System.out.println("==========================================");

        // Use inventory logs to rollback
        List<InventoryLog> logs = inventoryLogRepository.findByPieceworkId(id);
        for (InventoryLog log : logs) {
            if ("parts".equals(log.getInventoryType())) {
                // Rollback parts: subtract the quantity that was added
                rollbackInventoryByLog(log);
            } else if ("blank".equals(log.getInventoryType())) {
                // Rollback blank: add back the quantity that was consumed
                rollbackBlankByLog(log);
            }
        }

        // Delete logs
        inventoryLogRepository.deleteByPieceworkId(id);

        // Delete piecework record
        pieceWorkRepository.deleteById(id);
        System.out.println("Piecework deleted with " + logs.size() + " log entries rolled back");
    }

    private void rollbackInventoryByLog(InventoryLog log) {
        System.out.println("Rollback parts: " + log.getProductName() + " qty=" + log.getQuantityChange());
        List<InventoryItem> items = inventoryRepository.findAll();
        InventoryItem item = items.stream()
            .filter(i -> log.getProductName().equals(i.getProductName())
                && matchesSpecification(log.getSpecification(), i.getSpecification())
                && matchesMaterial(log.getMaterial(), i.getMaterial()))
            .findFirst().orElse(null);

        if (item != null) {
            int newQty = item.getQuantity() - log.getQuantityChange();
            if (newQty <= 0) {
                inventoryRepository.delete(item);
            } else {
                item.setQuantity(newQty);
                inventoryRepository.save(item);
            }
        }
    }

    private void rollbackBlankByLog(InventoryLog log) {
        System.out.println("Rollback blank: " + log.getProductName() + " qty=" + log.getQuantityChange());
        Optional<BlankInventory> blankOpt = blankInventoryRepository
            .findByProductNameAndSpecificationAndMaterial(
                log.getProductName(), log.getSpecification(), log.getMaterial());

        if (blankOpt.isPresent()) {
            BlankInventory blank = blankOpt.get();
            // log.getQuantityChange() is negative for consumption, so subtract it (add back)
            blank.setQuantity(blank.getQuantity() - log.getQuantityChange());
            blankInventoryRepository.save(blank);
        }
    }

    private void rollbackBlank(AutoStorageRule rule, PieceWork pieceWork, int quantity) {
        System.out.println("=== Rollback blank inventory ===");
        int requiredBlankQty = quantity * rule.getBlankQuantityPerUnit();

        Optional<BlankInventory> blankItemOpt = blankInventoryRepository
            .findByProductNameAndSpecificationAndMaterial(
                rule.getBlankProductName(),
                pieceWork.getSpecification(),
                pieceWork.getMaterial()
            );

        if (blankItemOpt.isPresent()) {
            BlankInventory blankItem = blankItemOpt.get();
            int newQuantity = blankItem.getQuantity() + requiredBlankQty;
            blankItem.setQuantity(newQuantity);
            blankInventoryRepository.save(blankItem);
            System.out.println("Blank rollback done, added: " + requiredBlankQty + ", now: " + newQuantity);
        } else {
            System.out.println("Blank not found, skip rollback");
        }
    }

    private void rollbackInventory(String targetLocation, PieceWork pieceWork, int quantity) {
        System.out.println("=== Rollback product inventory ===");
        List<InventoryItem> existingItems = inventoryRepository.findAll();
        InventoryItem existingItem = existingItems.stream()
            .filter(i -> targetLocation.equals(i.getProductName())
                && matchesSpecification(pieceWork.getSpecification(), i.getSpecification())
                && matchesMaterial(pieceWork.getMaterial(), i.getMaterial()))
            .findFirst()
            .orElse(null);

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() - quantity;
            if (newQuantity <= 0) {
                inventoryRepository.delete(existingItem);
                System.out.println("Inventory deleted (zero qty)");
            } else {
                existingItem.setQuantity(newQuantity);
                inventoryRepository.save(existingItem);
                System.out.println("Inventory reduced: " + quantity + ", now: " + newQuantity);
            }
        } else {
            System.out.println("Inventory not found, skip rollback");
        }
    }

    private AutoStorageRule findMatchingRule(String productName) {
        List<AutoStorageRule> enabledRules = ruleRepository.findByIsEnabledOrderByPriorityDesc(true);
        return enabledRules.stream()
            .filter(r -> {
                String pattern = r.getProductPattern();
                if (pattern == null) return false;
                String regex = java.util.regex.Pattern.quote(pattern).replace("\\Q%\\E", ".*");
                return productName != null && productName.matches(regex);
            })
            .findFirst()
            .orElse(null);
    }

    private void createOrUpdateInventory(String targetLocation, PieceWork pieceWork, int quantity) {
        List<InventoryItem> existingItems = inventoryRepository.findAll();
        InventoryItem existingItem = existingItems.stream()
            .filter(i -> targetLocation.equals(i.getProductName())
                && matchesSpecification(pieceWork.getSpecification(), i.getSpecification())
                && matchesMaterial(pieceWork.getMaterial(), i.getMaterial()))
            .findFirst()
            .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            inventoryRepository.save(existingItem);
            System.out.println("Update inventory: " + targetLocation + ", new qty: " + existingItem.getQuantity());
        } else {
            InventoryItem item = new InventoryItem();
            item.setProductName(targetLocation);
            item.setSpecification(pieceWork.getSpecification());
            item.setQuantity(quantity);
            item.setMaterial(pieceWork.getMaterial());
            item.setConnectionType(pieceWork.getConnectionType());
            item.setUnit(pieceWork.getUnit());
            item.setUnitPrice(pieceWork.getUnitPrice());
            InventoryItem savedItem = inventoryRepository.save(item);
            System.out.println("Create inventory: " + targetLocation + ", qty: " + savedItem.getQuantity());
        }
    }

    private void consumeBlank(AutoStorageRule rule, PieceWork pieceWork, int quantity) {
        System.out.println("=== Consume blank ===");
        System.out.println("Blank product: " + rule.getBlankProductName());

        int requiredBlankQty = quantity * rule.getBlankQuantityPerUnit();
        System.out.println("Required blank: " + requiredBlankQty);

        Optional<BlankInventory> blankItemOpt = blankInventoryRepository
            .findByProductNameAndSpecificationAndMaterial(
                rule.getBlankProductName(),
                pieceWork.getSpecification(),
                pieceWork.getMaterial()
            );

        BlankInventory blankItem;
        if (!blankItemOpt.isPresent()) {
            System.out.println("Blank not found, auto create");
            BlankInventory newBlank = new BlankInventory();
            newBlank.setProductName(rule.getBlankProductName());
            newBlank.setSpecification(pieceWork.getSpecification());
            newBlank.setMaterial(pieceWork.getMaterial());
            newBlank.setQuantity(0);
            newBlank.setUnit("个");
            newBlank.setRemarks("Auto created for piecework");
            blankItem = blankInventoryRepository.save(newBlank);
        } else {
            blankItem = blankItemOpt.get();
        }

        int newQuantity = blankItem.getQuantity() - requiredBlankQty;
        blankItem.setQuantity(newQuantity);
        blankInventoryRepository.save(blankItem);

        if (newQuantity < 0) {
            System.out.println("WARNING: Blank insufficient! Owed: " + Math.abs(newQuantity));
        } else {
            System.out.println("Blank consumed, remaining: " + newQuantity);
        }
    }

    private boolean matchesSpecification(String spec1, String spec2) {
        if (spec1 == null && spec2 == null) return true;
        if (spec1 == null || spec2 == null) return false;
        return spec1.equals(spec2);
    }

    private boolean matchesMaterial(String mat1, String mat2) {
        if (mat1 == null && mat2 == null) return true;
        if (mat1 == null || mat2 == null) return false;
        return mat1.equals(mat2);
    }

    public List<PieceWork> findAll() {
        return pieceWorkRepository.findAll();
    }

    public PieceWork findById(Long id) {
        return pieceWorkRepository.findById(id).orElse(null);
    }

    public void deleteById(Long id) {
        pieceWorkRepository.deleteById(id);
    }

    public List<PieceWork> findByWorkerName(String workerName) {
        return pieceWorkRepository.findByWorkerName(workerName);
    }

    public Object search(
            String role,
            String username,
            String workerName,
            Boolean queryAll,
            String startDate,
            String endDate,
            Integer page,
            Integer size) {

        String effectiveWorkerName;
        if (Boolean.TRUE.equals(queryAll)) {
            effectiveWorkerName = (workerName != null && !workerName.trim().isEmpty()) ? workerName.trim() : null;
        } else {
            effectiveWorkerName = username;
        }

        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;
        if (startDate != null && !startDate.trim().isEmpty() && endDate != null && !endDate.trim().isEmpty()) {
            try {
                LocalDate start = LocalDate.parse(startDate.trim());
                LocalDate end = LocalDate.parse(endDate.trim());
                startDateTime = start.atStartOfDay();
                endDateTime = end.atTime(LocalTime.MAX);
            } catch (Exception ignored) {
                startDateTime = null;
                endDateTime = null;
            }
        }

        LocalDateTime finalStart = startDateTime;
        LocalDateTime finalEnd = endDateTime;
        String finalWorker = effectiveWorkerName;

        Specification<PieceWork> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (finalWorker != null && !finalWorker.isEmpty()) {
                predicates.add(cb.equal(root.get("workerName"), finalWorker));
            }

            if (finalStart != null && finalEnd != null) {
                predicates.add(cb.between(root.get("workDate"), finalStart, finalEnd));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "workDate");

        if (page == null && size == null) {
            return pieceWorkRepository.findAll(spec, sort);
        }

        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Page<PieceWork> p = pieceWorkRepository.findAll(spec, pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("content", p.getContent());
        result.put("totalElements", p.getTotalElements());
        result.put("totalPages", p.getTotalPages());
        result.put("number", p.getNumber());
        result.put("size", p.getSize());
        result.put("first", p.isFirst());
        result.put("last", p.isLast());
        return result;
    }

    private static class AggRow {
        String day;
        String productName;
        String specification;
        String material;
        String connectionType;
        BigDecimal unitPrice;
        Long qty;
        BigDecimal amount;
        Long rows;
    }

    private String aggKey(AggRow r, boolean includeUnitPrice, boolean includeConnectionType) {
        String up = includeUnitPrice ? String.valueOf(r.unitPrice) : "";
        String ct = includeConnectionType ? String.valueOf(r.connectionType) : "";
        return String.join("|",
                Objects.toString(r.day, ""),
                Objects.toString(r.productName, ""),
                Objects.toString(r.specification, ""),
                Objects.toString(r.material, ""),
                ct,
                up);
    }

    private Map<String, AggRow> queryAgg(String workerName, LocalDateTime start, LocalDateTime end,
                                         boolean includeUnitPrice, boolean includeConnectionType) {

        List<String> selectCols = new ArrayList<>();
        List<String> groupCols = new ArrayList<>();

        selectCols.add("DATE(work_date) AS day");
        groupCols.add("DATE(work_date)");

        selectCols.add("product_name");
        groupCols.add("product_name");

        selectCols.add("specification");
        groupCols.add("specification");

        selectCols.add("material");
        groupCols.add("material");

        if (includeConnectionType) {
            selectCols.add("connection_type");
            groupCols.add("connection_type");
        } else {
            selectCols.add("'' AS connection_type");
        }

        if (includeUnitPrice) {
            selectCols.add("unit_price");
            groupCols.add("unit_price");
        } else {
            selectCols.add("0 AS unit_price");
        }

        selectCols.add("SUM(COALESCE(quantity,0)) AS qty");
        selectCols.add("SUM(COALESCE(total_amount,0)) AS amount");
        selectCols.add("COUNT(*) AS row_count");

        String sql = "SELECT " + String.join(", ", selectCols)
                + " FROM piece_works"
                + " WHERE worker_name = ? AND work_date >= ? AND work_date <= ?"
                + " GROUP BY " + String.join(", ", groupCols);

        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                sql,
                workerName,
                Timestamp.valueOf(start),
                Timestamp.valueOf(end)
        );

        Map<String, AggRow> out = new HashMap<>();
        for (Map<String, Object> m : list) {
            AggRow r = new AggRow();
            r.day = Objects.toString(m.get("day"), "");
            r.productName = Objects.toString(m.get("product_name"), "");
            r.specification = Objects.toString(m.get("specification"), "");
            r.material = Objects.toString(m.get("material"), "");
            r.connectionType = Objects.toString(m.get("connection_type"), "");
            Object up = m.get("unit_price");
            if (up instanceof BigDecimal) {
                r.unitPrice = (BigDecimal) up;
            } else if (up != null) {
                r.unitPrice = new BigDecimal(up.toString());
            } else {
                r.unitPrice = BigDecimal.ZERO;
            }
            Object q = m.get("qty");
            r.qty = q == null ? 0L : ((Number) q).longValue();
            Object amt = m.get("amount");
            if (amt instanceof BigDecimal) {
                r.amount = (BigDecimal) amt;
            } else if (amt != null) {
                r.amount = new BigDecimal(amt.toString());
            } else {
                r.amount = BigDecimal.ZERO;
            }
            Object rc = m.get("row_count");
            r.rows = rc == null ? 0L : ((Number) rc).longValue();

            out.put(aggKey(r, includeUnitPrice, includeConnectionType), r);
        }

        return out;
    }

    public Map<String, Object> reconcile(
            String userA,
            String userB,
            String startDate,
            String endDate,
            boolean includeUnitPrice,
            boolean includeConnectionType) {

        LocalDateTime start;
        LocalDateTime end;
        try {
            LocalDate s = LocalDate.parse(startDate.trim());
            LocalDate e = LocalDate.parse(endDate.trim());
            start = s.atStartOfDay();
            end = e.atTime(LocalTime.MAX);
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", "日期格式错误，应为 YYYY-MM-DD");
            err.put("onlyA", new ArrayList<>());
            err.put("onlyB", new ArrayList<>());
            err.put("diffs", new ArrayList<>());
            Map<String, Object> summary = new HashMap<>();
            summary.put("onlyACount", 0);
            summary.put("onlyBCount", 0);
            summary.put("diffCount", 0);
            err.put("summary", summary);
            return err;
        }

        Map<String, AggRow> a = queryAgg(userA, start, end, includeUnitPrice, includeConnectionType);
        Map<String, AggRow> b = queryAgg(userB, start, end, includeUnitPrice, includeConnectionType);

        Set<String> keys = new HashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());

        List<Map<String, Object>> onlyA = new ArrayList<>();
        List<Map<String, Object>> onlyB = new ArrayList<>();
        List<Map<String, Object>> diffs = new ArrayList<>();

        for (String k : keys) {
            AggRow ra = a.get(k);
            AggRow rb = b.get(k);
            if (ra != null && rb == null) {
                Map<String, Object> m = new HashMap<>();
                m.put("key", k);
                m.put("day", ra.day);
                m.put("productName", ra.productName);
                m.put("specification", ra.specification);
                m.put("material", ra.material);
                m.put("connectionType", ra.connectionType);
                m.put("unitPrice", ra.unitPrice);
                m.put("qty", ra.qty);
                m.put("amount", ra.amount);
                m.put("rows", ra.rows);
                onlyA.add(m);
                continue;
            }
            if (ra == null && rb != null) {
                Map<String, Object> m = new HashMap<>();
                m.put("key", k);
                m.put("day", rb.day);
                m.put("productName", rb.productName);
                m.put("specification", rb.specification);
                m.put("material", rb.material);
                m.put("connectionType", rb.connectionType);
                m.put("unitPrice", rb.unitPrice);
                m.put("qty", rb.qty);
                m.put("amount", rb.amount);
                m.put("rows", rb.rows);
                onlyB.add(m);
                continue;
            }
            if (ra != null && rb != null) {
                boolean qtyDiff = !Objects.equals(ra.qty, rb.qty);
                boolean amtDiff = ra.amount.compareTo(rb.amount) != 0;
                if (qtyDiff || amtDiff) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("key", k);
                    m.put("day", ra.day);
                    m.put("productName", ra.productName);
                    m.put("specification", ra.specification);
                    m.put("material", ra.material);
                    m.put("connectionType", ra.connectionType);
                    m.put("unitPrice", ra.unitPrice);
                    m.put("aQty", ra.qty);
                    m.put("bQty", rb.qty);
                    m.put("aAmt", ra.amount);
                    m.put("bAmt", rb.amount);
                    m.put("aRows", ra.rows);
                    m.put("bRows", rb.rows);
                    m.put("diffQty", ra.qty - rb.qty);
                    m.put("diffAmt", ra.amount.subtract(rb.amount));
                    diffs.add(m);
                }
            }
        }

        java.util.Comparator<Map<String, Object>> sort = (m1, m2) -> {
            String d1 = Objects.toString(m1.get("day"), "");
            String d2 = Objects.toString(m2.get("day"), "");
            int c = d2.compareTo(d1);
            if (c != 0) return c;
            String p1 = Objects.toString(m1.get("productName"), "");
            String p2 = Objects.toString(m2.get("productName"), "");
            return p1.compareTo(p2);
        };

        onlyA.sort(sort);
        onlyB.sort(sort);
        diffs.sort(sort);

        Map<String, Object> summary = new HashMap<>();
        summary.put("onlyACount", onlyA.size());
        summary.put("onlyBCount", onlyB.size());
        summary.put("diffCount", diffs.size());

        // 按产品统计总数
        Map<String, Map<String, Object>> productStatsA = new HashMap<>();
        Map<String, Map<String, Object>> productStatsB = new HashMap<>();
        
        for (AggRow r : a.values()) {
            String pn = r.productName;
            Map<String, Object> ps = productStatsA.computeIfAbsent(pn, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("productName", pn);
                m.put("totalQty", 0L);
                m.put("totalAmount", BigDecimal.ZERO);
                return m;
            });
            ps.put("totalQty", ((Long) ps.get("totalQty")) + r.qty);
            ps.put("totalAmount", ((BigDecimal) ps.get("totalAmount")).add(r.amount));
        }
        
        for (AggRow r : b.values()) {
            String pn = r.productName;
            Map<String, Object> ps = productStatsB.computeIfAbsent(pn, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("productName", pn);
                m.put("totalQty", 0L);
                m.put("totalAmount", BigDecimal.ZERO);
                return m;
            });
            ps.put("totalQty", ((Long) ps.get("totalQty")) + r.qty);
            ps.put("totalAmount", ((BigDecimal) ps.get("totalAmount")).add(r.amount));
        }
        
        List<Map<String, Object>> productSummaryA = new ArrayList<>(productStatsA.values());
        List<Map<String, Object>> productSummaryB = new ArrayList<>(productStatsB.values());
        productSummaryA.sort((m1, m2) -> ((String) m1.get("productName")).compareTo((String) m2.get("productName")));
        productSummaryB.sort((m1, m2) -> ((String) m1.get("productName")).compareTo((String) m2.get("productName")));
        
        summary.put("productSummaryA", productSummaryA);
        summary.put("productSummaryB", productSummaryB);

        Map<String, Object> out = new HashMap<>();
        out.put("userA", userA);
        out.put("userB", userB);
        out.put("startDate", startDate);
        out.put("endDate", endDate);
        out.put("includeUnitPrice", includeUnitPrice);
        out.put("includeConnectionType", includeConnectionType);
        out.put("onlyA", onlyA);
        out.put("onlyB", onlyB);
        out.put("diffs", diffs);
        out.put("summary", summary);
        return out;
    }
    
    /**
     * 解析入库比例并计算实际入库数量
     * @param quantity 原始数量
     * @param storageRatio 入库比例（如"2:1"）
     * @return 实际入库数量
     */
    private int calculateInventoryQuantity(int quantity, String storageRatio) {
        if (storageRatio == null || storageRatio.trim().isEmpty()) {
            return quantity; // 默认1:1
        }
        
        try {
            String[] parts = storageRatio.split(":");
            if (parts.length == 2) {
                int numerator = Integer.parseInt(parts[0].trim());
                int denominator = Integer.parseInt(parts[1].trim());
                
                if (denominator == 0) {
                    System.err.println("Invalid storage ratio denominator: " + storageRatio);
                    return quantity;
                }
                
                // 2:1 means 2 products -> 1 inventory, so factor = 1/2 = 0.5
                // 1:2 means 1 product -> 2 inventory, so factor = 2/1 = 2.0
                double factor = (double) denominator / numerator;
                int result = (int) (quantity * factor);
                
                System.out.println("Storage ratio " + storageRatio + ": " + quantity + " * " + factor + " = " + result);
                return result;
            }
        } catch (Exception e) {
            System.err.println("Error parsing storage ratio: " + storageRatio + ", error: " + e.getMessage());
        }
        
        return quantity; // fallback to 1:1
    }
    
    /**
     * 获取入库比例系数
     */
    private double getStorageRatioFactor(String storageRatio) {
        if (storageRatio == null || storageRatio.trim().isEmpty()) {
            return 1.0;
        }
        
        try {
            String[] parts = storageRatio.split(":");
            if (parts.length == 2) {
                int numerator = Integer.parseInt(parts[0].trim());
                int denominator = Integer.parseInt(parts[1].trim());
                
                if (numerator == 0) {
                    return 1.0;
                }
                
                return (double) denominator / numerator;
            }
        } catch (Exception e) {
            System.err.println("Error parsing storage ratio: " + storageRatio);
        }
        
        return 1.0;
    }
}

