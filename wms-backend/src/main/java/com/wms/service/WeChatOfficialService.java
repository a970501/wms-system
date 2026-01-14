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
public class WeChatOfficialService {

    private static final Logger log = LoggerFactory.getLogger(WeChatOfficialService.class);

    @Value("${wechat.official.app-id:}")
    private String appId;

    @Value("${wechat.official.app-secret:}")
    private String appSecret;

    @Value("${wechat.official.template-id:}")
    private String templateId;

    @Value("${wechat.official.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private long tokenExpireTime;

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SEND_TEMPLATE_MESSAGE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=";

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
                    log.info("微信公众号access_token获取成功");
                    return accessToken;
                } else {
                    log.error("获取微信公众号access_token失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("获取微信公众号access_token异常", e);
        }

        return null;
    }

    public boolean sendTemplateMessage(String openId, String title, String content, String url) {
        if (!isEnabled()) {
            log.warn("微信公众号模板消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送模板消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", openId);
            body.put("template_id", templateId);
            body.put("url", url);

            Map<String, Object> data = new HashMap<>();
            
            Map<String, Object> first = new HashMap<>();
            first.put("value", title);
            first.put("color", "#173177");
            data.put("first", first);

            Map<String, Object> keyword1 = new HashMap<>();
            keyword1.put("value", content);
            keyword1.put("color", "#173177");
            data.put("keyword1", keyword1);

            Map<String, Object> keyword2 = new HashMap<>();
            keyword2.put("value", java.time.LocalDateTime.now().toString());
            keyword2.put("color", "#173177");
            data.put("keyword2", keyword2);

            Map<String, Object> remark = new HashMap<>();
            remark.put("value", "请及时查看");
            remark.put("color", "#173177");
            data.put("remark", remark);

            body.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_TEMPLATE_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("微信公众号模板消息发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信公众号模板消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送微信公众号模板消息异常", e);
        }

        return false;
    }

    public boolean sendTemplateMessage(String openId, Map<String, String> templateData, String url) {
        if (!isEnabled()) {
            log.warn("微信公众号模板消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送模板消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", openId);
            body.put("template_id", templateId);
            body.put("url", url);

            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, String> entry : templateData.entrySet()) {
                Map<String, Object> field = new HashMap<>();
                field.put("value", entry.getValue());
                field.put("color", "#173177");
                data.put(entry.getKey(), field);
            }
            body.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_TEMPLATE_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("微信公众号模板消息发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信公众号模板消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送微信公众号模板消息异常", e);
        }

        return false;
    }

    public boolean sendTemplateMessage(String openId, String templateId, Map<String, String> templateData, String url, String miniprogramAppId, String miniprogramPagePath) {
        if (!isEnabled()) {
            log.warn("微信公众号模板消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送模板消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", openId);
            body.put("template_id", templateId);
            body.put("url", url);

            if (miniprogramAppId != null && !miniprogramAppId.isEmpty() && miniprogramPagePath != null) {
                Map<String, String> miniprogram = new HashMap<>();
                miniprogram.put("appid", miniprogramAppId);
                miniprogram.put("pagepath", miniprogramPagePath);
                body.put("miniprogram", miniprogram);
            }

            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<String, String> entry : templateData.entrySet()) {
                Map<String, Object> field = new HashMap<>();
                field.put("value", entry.getValue());
                field.put("color", "#173177");
                data.put(entry.getKey(), field);
            }
            body.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_TEMPLATE_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("微信公众号模板消息发送成功, openId: {}", openId);
                    return true;
                } else {
                    log.error("微信公众号模板消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送微信公众号模板消息异常", e);
        }

        return false;
    }
}
