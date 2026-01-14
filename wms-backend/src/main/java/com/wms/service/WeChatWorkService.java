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
public class WeChatWorkService {

    private static final Logger log = LoggerFactory.getLogger(WeChatWorkService.class);

    @Value("${wechat.work.corp-id:}")
    private String corpId;

    @Value("${wechat.work.agent-id:}")
    private String agentId;

    @Value("${wechat.work.secret:}")
    private String secret;

    @Value("${wechat.work.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private long tokenExpireTime;

    private static final String ACCESS_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String SEND_MESSAGE_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=";

    public boolean isEnabled() {
        return enabled && corpId != null && !corpId.isEmpty() 
            && agentId != null && !agentId.isEmpty()
            && secret != null && !secret.isEmpty();
    }

    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        try {
            String url = ACCESS_TOKEN_URL + "?corpid=" + corpId + "&corpsecret=" + secret;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    accessToken = (String) result.get("access_token");
                    tokenExpireTime = System.currentTimeMillis() + 7000 * 1000;
                    log.info("企业微信access_token获取成功");
                    return accessToken;
                } else {
                    log.error("获取企业微信access_token失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("获取企业微信access_token异常", e);
        }

        return null;
    }

    public boolean sendTextMessage(String userId, String content) {
        if (!isEnabled()) {
            log.warn("企业微信消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", userId);
            body.put("msgtype", "text");
            body.put("agentid", Integer.parseInt(agentId));

            Map<String, Object> text = new HashMap<>();
            text.put("content", content);
            body.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = SEND_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("企业微信文本消息发送成功, 用户: {}", userId);
                    return true;
                } else {
                    log.error("企业微信文本消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送企业微信文本消息异常", e);
        }

        return false;
    }

    public boolean sendMarkdownMessage(String userId, String markdown) {
        if (!isEnabled()) {
            log.warn("企业微信消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", userId);
            body.put("msgtype", "markdown");
            body.put("agentid", Integer.parseInt(agentId));

            Map<String, Object> markdownContent = new HashMap<>();
            markdownContent.put("content", markdown);
            body.put("markdown", markdownContent);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = SEND_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("企业微信Markdown消息发送成功, 用户: {}", userId);
                    return true;
                } else {
                    log.error("企业微信Markdown消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送企业微信Markdown消息异常", e);
        }

        return false;
    }

    public boolean sendTextCardMessage(String userId, String title, String description, String url) {
        if (!isEnabled()) {
            log.warn("企业微信消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", userId);
            body.put("msgtype", "textcard");
            body.put("agentid", Integer.parseInt(agentId));

            Map<String, Object> textcard = new HashMap<>();
            textcard.put("title", title);
            textcard.put("description", description);
            textcard.put("url", url);
            textcard.put("btntxt", "查看详情");
            body.put("textcard", textcard);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String sendUrl = SEND_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(sendUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("企业微信文本卡片消息发送成功, 用户: {}", userId);
                    return true;
                } else {
                    log.error("企业微信文本卡片消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送企业微信文本卡片消息异常", e);
        }

        return false;
    }

    public boolean sendFileMessage(String userId, String mediaId) {
        if (!isEnabled()) {
            log.warn("企业微信消息服务未启用或配置不完整");
            return false;
        }

        String token = getAccessToken();
        if (token == null) {
            log.error("无法获取access_token，发送消息失败");
            return false;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("touser", userId);
            body.put("msgtype", "file");
            body.put("agentid", Integer.parseInt(agentId));

            Map<String, Object> file = new HashMap<>();
            file.put("media_id", mediaId);
            body.put("file", file);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = SEND_MESSAGE_URL + token;

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
                Integer errcode = (Integer) result.get("errcode");
                
                if (errcode != null && errcode == 0) {
                    log.info("企业微信文件消息发送成功, 用户: {}", userId);
                    return true;
                } else {
                    log.error("企业微信文件消息发送失败: {}", result.get("errmsg"));
                }
            }
        } catch (Exception e) {
            log.error("发送企业微信文件消息异常", e);
        }

        return false;
    }
}
