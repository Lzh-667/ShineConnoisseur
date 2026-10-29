package com.lzh.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.lzh.common.BusinessException;

/** 统一分页步长；用于预判下一页的额外记录不能改变下一页的偏移量。 */
public final class Pagination {
    // 按页码跳转的查询最多展示 1,000 条记录（含预判下一页的额外记录时最多查询 1,001 条）。
    public static final int MAX_CURRENT = 100;
    private Pagination() {}

    public static void rejectWindowFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("result window is too large")) {
                throw new BusinessException("搜索结果超过可浏览范围，请缩小筛选条件");
            }
        }
    }

    public static int current(Integer current) {
        if (current != null && current > MAX_CURRENT) {
            throw new BusinessException("最多浏览前100页，请缩小筛选范围");
        }
        return current == null || current < 1 ? 1 : current;
    }

    public static long offset(Integer current) {
        return (long) (current(current) - 1) * SystemConstants.MAX_PAGE_SIZE;
    }

    public static <T> Page<T> lookahead(Integer current) {
        return new Page<T>(current(current), SystemConstants.MAX_PAGE_SIZE + 1, false) {
            @Override
            public long offset() {
                return (getCurrent() - 1) * SystemConstants.MAX_PAGE_SIZE;
            }
        };
    }

    public static PageRequest searchLookahead(Integer current) {
        return new PageRequest(current(current) - 1, SystemConstants.MAX_PAGE_SIZE + 1, Sort.unsorted()) {
            @Override
            public long getOffset() {
                return (long) getPageNumber() * SystemConstants.MAX_PAGE_SIZE;
            }
        };
    }
}
