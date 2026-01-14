package com.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WeChatMiniService {

    private static final Logger log = LoggerFactory.getLogger(WeChatMiniService.class);

    @Value("${wechat.mini.app-id:}")
    private String appId;

    @Value("${wechat.mini.app-secret:}")
    private String appSecret;

    @Value("${wechat.mini.template-id:}")
    private String templateId;

    @Value("${wechat.mini.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private long tokenExpireTime;

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SEND_SUBSCRIBE_MESSAGE_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=";

    public boolean isEnabled() {
        return enabled && appId != null && !appId.isEmpty() 
            && appSecret != null && !appSecret.isEmpty()
            && templateId != null && !templateId.isEmpty();
    }

    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        try {
            String url = ACCESS_TOKEN_URL + "?grant_type=client_credential&appid=" + appId + "&secret=" + appSecret;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode == null || errcode == 0) {
                    accessToken = (String) result.get("access_token");
                    tokenExpireTime = System.currentTimeMillis() + 7000 * 1000;
                    log.info("微信小程序access_token获取成功");
                    return accessToken;
                } else {
                    log.error("获取微信小程序access_token失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("获取微信小程序access_token异常", e);
        }

        return null;
    }

    public boolean sendSubscribeMessage(String openId, String title, String content, String page) {
        if (!isEnabled()) {
            log.warn("微信小程序订阅消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送订阅消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", openId);
            body.put("template_id", templateId);
            body.put("page", page);

            Map<String, Object> data = new HashMap<>();
            
            Map<String, String> thing1 = new HashMap<>();
            thing1.put("value", title);
            data.put("thing1", thing1);

            Map<String, String> thing2 = new HashMap<>();
            thing2.put("value", content);
            data.put("thing2", thing2);

            Map<String, String> date3 = new HashMap<>();
            date3.put("value", java.time.LocalDateTime.now().toString());
            data.put("date3", date3);

            body.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_SUBSCRIBE_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("微信小程序订阅消息发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信小程序订阅消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送微信小程序订阅消息异常", e);
        }

        return false;
    }

    public boolean sendSubscribeMessage(String openId, Map<String, String> templateData, String page) {
        if (!isEnabled()) {
            log.warn("微信小程序订阅消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送订阅消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", openId);
            body.put("template_id", templateId);
            body.put("page", page);

            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, String> entry : templateData.entrySet()) {
                Map<String, String> field = new HashMap<>();
                field.put("value", entry.getValue());
                data.put(entry.getKey(), field);
            }
            body.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_SUBSCRIBE_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("微信小程序订阅消息发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信小程序订阅消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送微信小程序订阅消息异常", e);
        }

        return false;
    }

    public boolean sendSubscribeMessage(String openId, String templateId, Map<String, String> templateData, String page) {
        if (!isEnabled()) {
            log.warn("微信小程序订阅消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送订阅消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", openId);
            body.put("template_id", templateId);
            body.put("page", page);

            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, String> entry : templateData.entrySet()) {
                Map<String, String> field = new HashMap<>();
                field.put("value", entry.getValue());
                data.put(entry.getKey(), field);
            }
            body.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_SUBSCRIBE_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("微信小程序订阅消息发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信小程序订阅消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送微信小程序订阅消息异常", e);
        }

        return false;
    }

    public boolean sendSubscribeMessage(String openId, String templateId, Map<String, String> templateData, String page, String miniprogramState) {
        if (!isEnabled()) {
            log.warn("微信小程序订阅消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送订阅消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", openId);
            body.put("template_id", templateId);
            body.put("page", page);
            body.put("miniprogram_state", miniprogramState);

            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, String> entry : templateData.entrySet()) {
                Map<String, String> field = new HashMap<>();
                field.put("value", entry.getValue());
                data.put(entry.getKey(), field);
            }
            body.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_SUBSCRIBE_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("微信小程序订阅消息发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信小程序订阅消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送微信小程序订阅消息异常", e);
        }

        return false;
    }
}
