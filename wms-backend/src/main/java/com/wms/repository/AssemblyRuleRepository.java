package com.wms.repository;
import com.wms.entity.AssemblyRule;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssemblyRuleRepository extends JpaRepository<AssemblyRule, Long> {
    AssemblyRule findByProductName(String productName);
}
