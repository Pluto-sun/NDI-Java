package com.pluto.ndi.shared.web;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PageResponse {
        items = List.copyOf(items);
        if (page < 0 || size < 0 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("分页参数不得为负数");
        }
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        Objects.requireNonNull(page, "page must not be null");
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
