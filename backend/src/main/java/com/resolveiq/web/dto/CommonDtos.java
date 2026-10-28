package com.resolveiq.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Small shared response shapes. */
public final class CommonDtos {

    private CommonDtos() {
    }

    public record Ref(String code, String name) {
    }

    public record PersonRef(Long id, String name, String role) {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record Message(String message) {
    }
}
