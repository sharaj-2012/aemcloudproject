package com.aemcloudproject.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UserResponseDTO {
    private int page;

    @JsonProperty("per_page")
    private int perPage;
    private int total;

    @JsonProperty("total_pages")
    private int totalPages;
    private List<UserDTO> data;
    private SupportDTO support;

    // Constructors
    public UserResponseDTO() {}

    public UserResponseDTO(int page, int perPage, int total, int totalPages, List<UserDTO> data, SupportDTO support) {
        this.page = page;
        this.perPage = perPage;
        this.total = total;
        this.totalPages = totalPages;
        this.data = data;
        this.support = support;
    }

    // Getters and Setters
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPerPage() { return perPage; }
    public void setPerPage(int perPage) { this.perPage = perPage; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public List<UserDTO> getData() { return data; }
    public void setData(List<UserDTO> data) { this.data = data; }

    public SupportDTO getSupport() { return support; }
    public void setSupport(SupportDTO support) { this.support = support; }
}
