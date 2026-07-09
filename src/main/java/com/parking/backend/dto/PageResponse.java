package com.parking.backend.dto;

import java.util.List;

import lombok.Data;

@Data
public class PageResponse<T> {

    private List<T> content;

    private long totalElements;

    private int totalPages;

    private int currentPage;

    private int pageSize;

    private boolean first;

    private boolean last;

    private boolean hasNext;

    private boolean hasPrevious;
}