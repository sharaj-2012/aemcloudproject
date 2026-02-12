package com.aemcloudproject.core.dto;

public class SupportDTO {
    private String url;
    private String text;

    // Constructors
    public SupportDTO() {}

    public SupportDTO(String url, String text) {
        this.url = url;
        this.text = text;
    }

    // Getters and Setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
