package com.wms.repository;
import com.wms.entity.AutoStorageRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AutoStorageRuleRepository extends JpaRepository<AutoStorageRule, Long> {
    List<AutoStorageRule> findByIsEnabledOrderByPriorityDesc(Boolean isEnabled);
    AutoStorageRule findByProductPattern(String productPattern);
}
