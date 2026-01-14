package com.wms.controller;

import com.wms.entity.FinishedProduct;
import com.wms.service.FinishedProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/finished-products")
@CrossOrigin
public class FinishedProductController {
    
    @Autowired
    private FinishedProductService service;
    
    @GetMapping
    public ResponseEntity<List<FinishedProduct>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<FinishedProduct> getById(@PathVariable Long id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinishedProduct> update(@PathVariable Long id, @RequestBody FinishedProduct product) {
        product.setId(id);
        return ResponseEntity.ok(service.save(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
