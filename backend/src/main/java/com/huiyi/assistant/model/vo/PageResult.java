package com.huiyi.assistant.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private int page;
    private int pageSize;
    private long total;

    public static <T> PageResult<T> of(List<T> all, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((safePage - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResult.<T>builder()
                .items(all.subList(from, to))
                .page(safePage)
                .pageSize(size)
                .total(all.size())
                .build();
    }
}
