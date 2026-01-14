package com.wms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * CORS跨域配置
 * 【安全加固】限制允许的域名，不使用通配符*
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:https://cl.bxyxr.com,http://localhost:8080,http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 【安全加固】只允许配置的域名跨域，不使用通配符
        Arrays.stream(allowedOrigins.split(","))
                .forEach(config::addAllowedOrigin);

        // 允许常用请求头
        config.addAllowedHeader("Authorization");
        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("Accept");
        config.addAllowedHeader("Origin");
        config.addAllowedHeader("X-Requested-With");

        // 允许常用HTTP方法
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");

        // 允许携带凭证
        config.setAllowCredentials(true);

        // 预检请求缓存时间（1小时）
        config.setMaxAge(3600L);

        // 对所有路径应用CORS配置
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
