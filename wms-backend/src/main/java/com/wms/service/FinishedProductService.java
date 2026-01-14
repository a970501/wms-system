package com.wms.service;

import com.wms.entity.FinishedProduct;
import com.wms.repository.FinishedProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FinishedProductService {
    
    @Autowired
    private FinishedProductRepository repository;
    
    public List<FinishedProduct> findAll() {
        return repository.findAll();
    }
    
    public Optional<FinishedProduct> findById(Long id) {
        return repository.findById(id);
    }
    
    @Transactional
    public FinishedProduct createOrUpdate(String productName, String specification, 
                                          String material, String connectionType, 
                                          int quantity, Long assemblyRecordId) {
        System.out.println("=== 成品入库 ===");
        System.out.println("产品: " + productName);
        System.out.println("规格: " + specification);
        System.out.println("材料: " + material);
        System.out.println("连接类型: " + connectionType);
        System.out.println("数量: " + quantity);
        
        Optional<FinishedProduct> existingOpt = repository
            .findByProductNameAndSpecificationAndMaterialAndConnectionType(
                productName, specification, material, connectionType);
        
        FinishedProduct product;
        if (existingOpt.isPresent()) {
            product = existingOpt.get();
            int newQuantity = product.getQuantity() + quantity;
            product.setQuantity(newQuantity);
            product.setAssemblyRecordId(assemblyRecordId);
            System.out.println("更新现有记录 ID: " + product.getId() + ", 新数量: " + newQuantity);
        } else {
            product = new FinishedProduct();
            product.setProductName(productName);
            product.setSpecification(specification);
            product.setMaterial(material);
            product.setConnectionType(connectionType);
            product.setQuantity(quantity);
            product.setAssemblyRecordId(assemblyRecordId);
            product.setRemarks("装配入库");
            System.out.println("创建新记录");
        }
        
        product = repository.save(product);
        System.out.println("✓ 成品入库成功 ID: " + product.getId());
        return product;
    }

    public FinishedProduct save(FinishedProduct product) {
        return repository.save(product);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
