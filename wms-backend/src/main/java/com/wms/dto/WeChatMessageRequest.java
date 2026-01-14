package com.wms.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 微信消息发送请求DTO
 */
public class WeChatMessageRequest {

    @NotBlank(message = "openid不能为空")
    private String openid;

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    private String page;
    private String url;
    private String messageType = "text"; // text, template, subscribe
    private String templateId;
    private LocalDateTime timestamp;

    public WeChatMessageRequest() {
        this.timestamp = LocalDateTime.now();
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "WeChatMessageRequest{" +
                "openid='" + openid + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", page='" + page + '\'' +
                ", url='" + url + '\'' +
                ", messageType='" + messageType + '\'' +
                ", templateId='" + templateId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}