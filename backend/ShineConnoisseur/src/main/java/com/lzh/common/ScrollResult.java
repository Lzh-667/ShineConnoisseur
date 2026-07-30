package com.lzh.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ScrollResult<T> {
    private List<T> list;
    private Boolean hasMore;
}
