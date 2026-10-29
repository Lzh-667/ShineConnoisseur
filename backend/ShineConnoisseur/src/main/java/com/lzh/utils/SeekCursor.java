package com.lzh.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lzh.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** 按复合排序键降序进行游标分页；列名只能由服务端代码提供。 */
public final class SeekCursor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private SeekCursor() {}
    public record Position(String scope, List<String> values) {}

    public static String encode(String scope, Object... values) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(
                    new Position(scope, java.util.Arrays.stream(values).map(Objects::toString).toList())));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encode pagination cursor", e);
        }
    }

    public static <T> QueryWrapper<T> after(String token, String scope, String... columns) {
        QueryWrapper<T> filter = new QueryWrapper<>();
        apply(filter, token, scope, columns);
        return filter;
    }

    public static <T> void apply(QueryWrapper<T> filter, String token, String scope, String... columns) {
        if (token == null || token.isBlank()) return;
        try {
            if (token.length() > 2048) throw new IllegalArgumentException();
            Position position = JSON.readValue(Base64.getUrlDecoder().decode(token), Position.class);
            if (!scope.equals(position.scope()) || position.values() == null
                    || position.values().size() != columns.length
                    || position.values().stream().anyMatch(v -> v == null || v.length() > 64)) {
                throw new IllegalArgumentException();
            }
            java.util.ArrayList<Object> values = new java.util.ArrayList<>();
            for (int i = 0; i < columns.length; i++) {
                String value = position.values().get(i);
                if (columns[i].equals("create_time")) {
                    values.add(java.time.LocalDateTime.parse(value));
                } else {
                    long number = Long.parseLong(value);
                    if (number < 0 || (columns[i].equals("id") && number == 0)) throw new IllegalArgumentException();
                    values.add(number);
                }
            }
            for (int i = 0; i < columns.length; i++) {
                final int index = i;
                filter.or(part -> {
                    for (int j = 0; j < index; j++) part.eq(columns[j], values.get(j));
                    part.lt(columns[index], values.get(index));
                });
            }
        } catch (Exception e) {
            throw new BusinessException("分页游标无效，请刷新列表");
        }
    }
}
