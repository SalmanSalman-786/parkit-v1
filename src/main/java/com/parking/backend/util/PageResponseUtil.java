package com.parking.backend.util;

import org.springframework.data.domain.Page;

import com.parking.backend.dto.PageResponse;

public class PageResponseUtil {

    private PageResponseUtil() {
}

    public static <T> PageResponse<T> from(Page<T> page) {

        PageResponse<T> response = new PageResponse<>();

        response.setContent(page.getContent());

        response.setTotalElements(page.getTotalElements());

        response.setTotalPages(page.getTotalPages());

        response.setCurrentPage(page.getNumber());

        response.setPageSize(page.getSize());

        response.setFirst(page.isFirst());

        response.setLast(page.isLast());

        response.setHasNext(page.hasNext());

        response.setHasPrevious(page.hasPrevious());

        return response;
    }
}