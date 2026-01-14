package com.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class WmsApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
    
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
