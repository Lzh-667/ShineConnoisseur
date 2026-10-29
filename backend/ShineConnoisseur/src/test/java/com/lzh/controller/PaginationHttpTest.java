package com.lzh.controller;

import com.lzh.common.BusinessException;
import com.lzh.common.Result;
import com.lzh.common.ScrollResult;
import com.lzh.config.WebExceptionAdvice;
import com.lzh.service.IReviewCommentService;
import com.lzh.service.IReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaginationHttpTest {
    @Test
    void cursorRoutesPassTokensAndReturnContinuation() throws Exception {
        var reviews = mock(IReviewService.class);
        var comments = mock(IReviewCommentService.class);
        var reviewController = new ReviewController();
        var commentController = new ReviewCommentController();
        ReflectionTestUtils.setField(reviewController, "reviewService", reviews);
        ReflectionTestUtils.setField(commentController, "reviewCommentService", comments);
        var mvc = MockMvcBuilders.standaloneSetup(reviewController, commentController)
                .setControllerAdvice(new WebExceptionAdvice()).build();
        Result page = Result.ok(new ScrollResult<>(List.of(1), true, "next"));
        when(reviews.myReviewsCursor(null)).thenReturn(page);
        when(reviews.getUserReviewsCursor(2L, "next")).thenReturn(page);
        when(comments.listRootReviewCommentCursor(3L, "next")).thenReturn(page);
        when(comments.listChildReviewCommentCursor(4L, "next")).thenReturn(page);
        when(comments.myReviewCommentsCursor("next")).thenReturn(page);
        mvc.perform(get("/reviews/scroll/my")).andExpect(jsonPath("$.data.nextCursor").value("next"));
        for (String path : List.of("/reviews/scroll/user/2", "/reviewComments/scroll/root/3",
                "/reviewComments/scroll/children/4", "/reviewComments/scroll/my")) {
            mvc.perform(get(path).param("cursor", "next"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(true))
                    .andExpect(jsonPath("$.data.nextCursor").value("next"));
        }
        when(reviews.searchReviews("test", 101, null)).thenThrow(new BusinessException("最多浏览前100页"));
        mvc.perform(get("/reviews/search").param("keyword", "test").param("current", "101"))
                .andExpect(jsonPath("$.success").value(false));
    }
}
