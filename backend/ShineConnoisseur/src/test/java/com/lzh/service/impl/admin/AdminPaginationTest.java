package com.lzh.service.impl.admin;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.common.PageResult;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.mapper.ReviewMapper;
import com.lzh.po.Movie;
import com.lzh.po.User;
import com.lzh.service.IMovieService;
import com.lzh.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminPaginationTest {
    @Test
    void usersNormalizePageAndUseStableOrder() {
        IUserService users = mock(IUserService.class);
        QueryChainWrapper<User> query = mock(QueryChainWrapper.class, RETURNS_SELF);
        when(query.orderByDesc(anyString())).thenReturn(query);
        when(users.query()).thenReturn(query);
        when(query.page(any(Page.class))).thenAnswer(call -> checkedPage(call.getArgument(0)));
        var service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "userService", users);
        assertEquals(12L, ((PageResult<?>) service.list(-1).getData()).getTotal());
        verify(query).orderByDesc("create_time");
        verify(query).orderByDesc("id");
    }

    @Test
    void moviesNormalizePageAndUseStableOrder() {
        IMovieService movies = mock(IMovieService.class);
        QueryChainWrapper<Movie> query = mock(QueryChainWrapper.class, RETURNS_SELF);
        when(query.orderByDesc(anyString())).thenReturn(query);
        when(movies.query()).thenReturn(query);
        when(query.page(any(Page.class))).thenAnswer(call -> checkedPage(call.getArgument(0)));
        var service = new AdminMovieServiceImpl();
        ReflectionTestUtils.setField(service, "movieService", movies);
        assertEquals(12L, ((PageResult<?>) service.listMovies(0).getData()).getTotal());
        verify(query).orderByDesc("id");
    }

    @Test
    void customMapperPagesNormalizeAndRetainTotal() {
        ReviewMapper reviews = mock(ReviewMapper.class);
        when(reviews.selectAdminPage(any())).thenAnswer(call -> checkedPage(call.getArgument(0)));
        var reviewService = new AdminReviewServiceImpl();
        ReflectionTestUtils.setField(reviewService, "reviewMapper", reviews);
        assertEquals(12L, ((PageResult<?>) reviewService.listReviews(null).getData()).getTotal());

        ReviewCommentMapper comments = mock(ReviewCommentMapper.class);
        when(comments.selectAdminPage(any())).thenAnswer(call -> checkedPage(call.getArgument(0)));
        var commentService = new AdminCommentServiceImpl();
        ReflectionTestUtils.setField(commentService, "reviewCommentMapper", comments);
        assertEquals(12L, ((PageResult<?>) commentService.listComments(-5).getData()).getTotal());
    }

    private <T> Page<T> checkedPage(Page<T> page) {
        assertEquals(1, page.getCurrent());
        assertEquals(10, page.getSize());
        assertTrue(page.searchCount());
        page.setTotal(12);
        return page.setRecords(List.of());
    }
}
