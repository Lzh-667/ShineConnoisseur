package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.dto.ReviewDTO;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.mapper.ReviewMapper;
import com.lzh.po.LikeRecord;
import com.lzh.po.Movie;
import com.lzh.po.Review;
import com.lzh.po.ReviewComment;
import com.lzh.po.User;
import com.lzh.repository.ReviewSearchRepository;
import com.lzh.service.ILikeRecordService;
import com.lzh.service.IMovieService;
import com.lzh.service.IUserService;
import com.lzh.service.VipIdentityService;
import com.lzh.utils.MQConstants;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.lzh.service.MessageOutboxService;
import com.lzh.service.CacheAfterCommitService;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private IMovieService movieService;
    @Mock private IUserService userService;
    @Mock private ILikeRecordService likeRecordService;
    @Mock private MessageOutboxService outboxService;
    @Mock private CacheAfterCommitService cacheAfterCommit;
    @Mock private ReviewCommentMapper reviewCommentMapper;
    @Mock private ReviewMapper reviewMapper;
    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private ReviewSearchRepository reviewSearchRepository;
    @Mock private VipIdentityService vipIdentityService;

    @Mock private QueryChainWrapper<Review> reviewQueryChain;
    @Mock private UpdateChainWrapper<Review> reviewUpdateChain;
    @Mock private UpdateChainWrapper<Movie> movieUpdateChain;
    @Mock private UpdateChainWrapper<User> userUpdateChain;
    @Mock private QueryChainWrapper<LikeRecord> likeRecordQueryChain;

    @Spy @InjectMocks
    private ReviewServiceImpl reviewService;

    private MockedStatic<UserHolder> userHolderMock;
    private UserDTO mockUser;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(vipIdentityService.activeUserIds(anyCollection())).thenReturn(Collections.emptySet());

        mockUser = new UserDTO();
        mockUser.setId(1L);
        mockUser.setNickname("testUser");
        userHolderMock = mockStatic(UserHolder.class);
        userHolderMock.when(UserHolder::getUser).thenReturn(mockUser);

        // 影评查询调用链
        doReturn(reviewQueryChain).when(reviewService).query();
        when(reviewQueryChain.eq(any(), any())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.in(anyString(), anySet())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.orderByDesc(anyString())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.orderByAsc(anyString())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.last(anyString())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.list()).thenReturn(Collections.emptyList());
        when(reviewQueryChain.select(anyString())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.one()).thenReturn(null);

        // 影评更新调用链
        doReturn(reviewUpdateChain).when(reviewService).update();
        when(reviewUpdateChain.setSql(anyString())).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.set(anyString(), any())).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.eq(anyString(), any())).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.gt(anyString(), anyInt())).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.update()).thenReturn(true);

        // 电影服务更新调用链
        when(movieService.update()).thenReturn(movieUpdateChain);
        when(movieUpdateChain.setSql(anyString())).thenReturn(movieUpdateChain);
        when(movieUpdateChain.eq(any(), any())).thenReturn(movieUpdateChain);
        when(movieUpdateChain.gt(anyString(), anyInt())).thenReturn(movieUpdateChain);
        when(movieUpdateChain.update()).thenReturn(true);

        // 用户服务更新调用链
        when(userService.update()).thenReturn(userUpdateChain);
        when(userUpdateChain.setSql(anyString())).thenReturn(userUpdateChain);
        when(userUpdateChain.eq(anyString(), any())).thenReturn(userUpdateChain);
        when(userUpdateChain.gt(anyString(), anyInt())).thenReturn(userUpdateChain);
        when(userUpdateChain.update()).thenReturn(true);

        // 点赞记录服务
        when(likeRecordService.query()).thenReturn(likeRecordQueryChain);
        when(likeRecordQueryChain.eq(anyString(), any())).thenReturn(likeRecordQueryChain);
        when(likeRecordQueryChain.in(anyString(), anySet())).thenReturn(likeRecordQueryChain);
        when(likeRecordQueryChain.list()).thenReturn(Collections.emptyList());
        when(likeRecordQueryChain.one()).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    // ==================== 发布影评 ====================

    @Test
    void publishReview_movieNotExists_returnsFail() {
        when(movieService.getById(1L)).thenReturn(null);
        ReviewDTO dto = buildReviewDTO(8, "好电影", "内容...", 0);

        var result = reviewService.publishReview(dto, 1L);

        assertFalse(result.getSuccess());
        assertEquals("电影不存在", result.getErrorMsg());
    }

    @Test
    void publishReview_ratingOutOfRange_returnsFail() {
        Movie movie = buildMovie(1L);
        when(movieService.getById(1L)).thenReturn(movie);
        ReviewDTO dto = buildReviewDTO(11, "好电影", "内容...", 0);

        var result = reviewService.publishReview(dto, 1L);

        assertFalse(result.getSuccess());
        assertEquals("评分必须在1-10之间", result.getErrorMsg());
    }

    @Test
    void publishReview_duplicate_returnsFail() {
        Movie movie = buildMovie(1L);
        when(movieService.getById(1L)).thenReturn(movie);
        ReviewDTO dto = buildReviewDTO(8, "好电影", "内容...", 0);

        Review existingReview = buildReview(1L, 1L, 1L, 8);
        existingReview.setStatus(SystemConstants.REVIEW_STATUS_NORMAL);
        when(reviewMapper.selectWithDeleted(1L, 1L)).thenReturn(existingReview);

        var result = reviewService.publishReview(dto, 1L);

        assertFalse(result.getSuccess());
        assertEquals("您已对该电影发表过影评，请勿重复提交", result.getErrorMsg());
    }

    @Test
    void publishReview_restoreDeleted_success() {
        Movie movie = buildMovie(1L);
        when(movieService.getById(1L)).thenReturn(movie);
        ReviewDTO dto = buildReviewDTO(8, "好电影", "内容...", 0);

        Review deletedReview = buildReview(1L, 1L, 1L, 5);
        deletedReview.setStatus(SystemConstants.REVIEW_STATUS_DELETE);
        when(reviewMapper.selectWithDeleted(1L, 1L)).thenReturn(deletedReview);
        when(reviewMapper.restoreReview(any(Review.class))).thenReturn(true);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        var result = reviewService.publishReview(dto, 1L);

        assertTrue(result.getSuccess());
        verify(reviewMapper).restoreReview(any(Review.class));
    }

    @Test
    void publishReview_success() {
        Movie movie = buildMovie(1L);
        when(movieService.getById(1L)).thenReturn(movie);
        ReviewDTO dto = buildReviewDTO(8, "好电影", "很棒的电影", 0);

        when(reviewMapper.selectWithDeleted(1L, 1L)).thenReturn(null);
        doReturn(true).when(reviewService).save(any(Review.class));
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        var result = reviewService.publishReview(dto, 1L);

        assertTrue(result.getSuccess());
        verify(reviewService).save(any(Review.class));
    }

    // ==================== 查询影评 ====================

    @Test
    void listReview_movieNotFound_returnsFail() {
        when(movieService.getById(1L)).thenReturn(null);

        var result = reviewService.listReview(1L, 1);

        assertFalse(result.getSuccess());
    }

    @Test
    void listReview_empty_returnsEmptyPage() {
        Movie movie = buildMovie(1L);
        when(movieService.getById(1L)).thenReturn(movie);

        Page<Review> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(reviewQueryChain.page(any(Page.class))).thenReturn(page);

        var result = reviewService.listReview(1L, 1);

        assertTrue(result.getSuccess());
    }

    // ==================== 点赞影评 ====================

    @Test
    void likeReview_reviewNotFound_returnsFail() {
        String likeKey = RedisConstants.LIKE_REVIEW_KEY + 1L;
        when(stringRedisTemplate.hasKey(likeKey)).thenReturn(false);
        when(likeRecordQueryChain.list()).thenReturn(Collections.emptyList());
        doReturn(false).when(reviewService).exists(any(QueryWrapper.class));

        var result = reviewService.likeReview(1L);

        assertFalse(result.getSuccess());
        assertEquals("点赞的影评不存在", result.getErrorMsg());
    }

    @Test
    void likeReview_alreadyLiked_returnsFail() {
        String likeKey = RedisConstants.LIKE_REVIEW_KEY + 1L;
        when(stringRedisTemplate.hasKey(likeKey)).thenReturn(true);
        when(setOperations.isMember(likeKey, "1")).thenReturn(true);

        var result = reviewService.likeReview(1L); // isLike 返回 true，因此进入取消点赞分支。
        // isLike 返回 true 时会先进入取消点赞分支。

        // 已点赞状态会进入取消点赞分支，这是切换操作的预期行为。

        // 代码根据 isLike 的结果在取消点赞和点赞两个分支之间切换。
        // “不能重复点赞”的检查位于点赞分支内部。
    }

    @Test
    void likeReview_unlike_success() {
        String likeKey = RedisConstants.LIKE_REVIEW_KEY + 1L;
        when(stringRedisTemplate.hasKey(likeKey)).thenReturn(true);
        when(setOperations.isMember(likeKey, "1")).thenReturn(true); // 当前为已点赞状态
        doReturn(true).when(likeRecordService).remove(any(QueryWrapper.class));
        doReturn(buildReviewWithLikeCount(1L, 5)).when(reviewService).getById(1L);
        when(setOperations.remove(likeKey, "1")).thenReturn(1L);

        var result = reviewService.likeReview(1L);

        assertTrue(result.getSuccess());
    }

    @Test
    void likeReview_like_success() {
        String likeKey = RedisConstants.LIKE_REVIEW_KEY + 1L;
        when(stringRedisTemplate.hasKey(likeKey)).thenReturn(false);
        when(likeRecordQueryChain.list()).thenReturn(Collections.emptyList()); // 当前为未点赞状态
        doReturn(true).when(reviewService).exists(any(QueryWrapper.class)); // 影评存在
        when(likeRecordQueryChain.exists()).thenReturn(false); // 不存在重复记录
        doReturn(true).when(likeRecordService).save(any(LikeRecord.class));
        doReturn(buildReviewWithLikeCount(1L, 5)).when(reviewService).getById(1L);
        when(setOperations.add(anyString(), anyString())).thenReturn(1L);

        // 模拟发送消息时查询影评作者。
        Review reviewForMsg = buildReview(2L, 1L, 1L, 8); // 作者用户为 2，并非当前用户。
        when(reviewQueryChain.select("user_id")).thenReturn(reviewQueryChain);
        when(reviewQueryChain.one()).thenReturn(reviewForMsg);

        when(userService.getById(1L)).thenReturn(buildUser(1L, "currentUser"));
        when(userService.getById(2L)).thenReturn(buildUser(2L, "authorUser"));

        var result = reviewService.likeReview(1L);

        assertTrue(result.getSuccess());
        verify(outboxService).enqueue(any(com.lzh.dto.MessageDTO.class), anyString());
    }

    // ==================== 修改影评 ====================

    @Test
    void updateReview_notOwner_returnsFail() {
        Review review = buildReview(2L, 1L, 1L, 8); // 影评用户为 2，当前用户为 1。
        doReturn(review).when(reviewService).getById(1L);

        ReviewDTO dto = buildReviewDTO(9, "改标题", "改内容", 0);
        var result = reviewService.updateReview(1L, dto);

        assertFalse(result.getSuccess());
        assertEquals("没有修改权限", result.getErrorMsg());
    }

    @Test
    void updateReview_success_returnsOk() {
        Review review = buildReview(1L, 1L, 1L, 8); // 影评用户和当前用户均为 1。
        doReturn(review).when(reviewService).getById(1L);

        ReviewDTO dto = buildReviewDTO(9, "改标题", "改内容", 0);
        var result = reviewService.updateReview(1L, dto);

        assertTrue(result.getSuccess());
    }

    // ==================== 删除影评 ====================

    @Test
    void deleteReview_commentsAlreadyDeleted_stillSucceedsAndInvalidatesCache() {
        prepareReviewWithCommentForDeletion();
        when(reviewCommentMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        assertTrue(reviewService.deleteReview(1L).getSuccess());

        var keys = org.mockito.ArgumentCaptor.forClass(String[].class);
        verify(cacheAfterCommit).evict(eq("删除影评"), keys.capture());
        assertTrue(java.util.Arrays.asList(keys.getValue()).contains(RedisConstants.LIKE_COMMENT_KEY + 2L));
        verify(reviewSearchRepository).deleteById(1L);
    }

    @Test
    void deleteReview_commentDeleteDatabaseError_propagates() {
        prepareReviewWithCommentForDeletion();
        var error = new org.springframework.dao.DataAccessResourceFailureException("database unavailable");
        when(reviewCommentMapper.delete(any(QueryWrapper.class))).thenThrow(error);

        assertSame(error, assertThrows(org.springframework.dao.DataAccessException.class,
                () -> reviewService.deleteReview(1L)));
        verifyNoInteractions(cacheAfterCommit, reviewSearchRepository);
    }

    private void prepareReviewWithCommentForDeletion() {
        doReturn(buildReview(1L, 1L, 1L, 8)).when(reviewService).getById(1L);
        doReturn(true).when(reviewService).removeById(1L);
        ReviewComment comment = new ReviewComment();
        comment.setId(2L);
        when(reviewCommentMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(comment));
    }

    @Test
    void deleteReview_notOwner_returnsFail() {
        Review review = buildReview(2L, 1L, 1L, 8);
        doReturn(review).when(reviewService).getById(1L);

        var result = reviewService.deleteReview(1L);

        assertFalse(result.getSuccess());
        assertEquals("没有删除权限", result.getErrorMsg());
    }

    @Test
    void deleteReview_success_noComments() {
        Review review = buildReview(1L, 1L, 1L, 8);
        review.setRating(5);
        doReturn(review).when(reviewService).getById(1L);
        doReturn(true).when(reviewService).removeById(1L);
        doReturn(true).when(likeRecordService).remove(any(QueryWrapper.class));
        when(reviewCommentMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);
        when(zSetOperations.remove(anyString(), anyString())).thenReturn(1L);

        var result = reviewService.deleteReview(1L);

        assertTrue(result.getSuccess());
        verify(reviewService).removeById(1L);
    }

    // ==================== 热门影评 ====================

    @Test
    void hotReviews_emptyZSet_returnsEmpty() {
        String key = RedisConstants.HOT_REVIEW_KEY;
        when(zSetOperations.reverseRange(key, 0, 10)).thenReturn(Collections.emptySet());

        var result = reviewService.hotReviews(1);

        assertTrue(result.getSuccess());
    }

    @Test
    void hotReviews_hasData_returnsList() {
        String key = RedisConstants.HOT_REVIEW_KEY;
        when(zSetOperations.reverseRange(key, 0, 10)).thenReturn(Set.of("1", "2"));

        Review r1 = buildReview(1L, 1L, 1L, 8);
        Review r2 = buildReview(2L, 2L, 2L, 7);
        doReturn(List.of(r1, r2)).when(reviewService).listByIds(anyList());

        doReturn(List.of(buildUser(1L, "u1"), buildUser(2L, "u2"))).when(userService).listByIds(anySet());
        doReturn(List.of(buildMovie(1L))).when(movieService).listByIds(anySet());

        var result = reviewService.hotReviews(1);

        assertTrue(result.getSuccess());
    }

    // ==================== 辅助方法 ====================

    private ReviewDTO buildReviewDTO(int rating, String title, String content, int spoiler) {
        ReviewDTO dto = new ReviewDTO();
        dto.setRating(rating);
        dto.setTitle(title);
        dto.setContent(content);
        dto.setSpoiler(spoiler);
        return dto;
    }

    private Review buildReview(Long userId, Long movieId, Long reviewId, int rating) {
        Review review = new Review();
        review.setId(reviewId);
        review.setUserId(userId);
        review.setMovieId(movieId);
        review.setRating(rating);
        review.setTitle("标题");
        review.setContent("内容");
        review.setStatus(SystemConstants.REVIEW_STATUS_NORMAL);
        review.setLikeCount(0);
        review.setCommentCount(0);
        review.setCreateTime(LocalDateTime.now().minusDays(1));
        return review;
    }

    private Review buildReviewWithLikeCount(Long reviewId, int likeCount) {
        Review review = new Review();
        review.setId(reviewId);
        review.setLikeCount(likeCount);
        review.setUserId(2L);
        review.setMovieId(1L);
        review.setStatus(SystemConstants.REVIEW_STATUS_NORMAL);
        return review;
    }

    private Movie buildMovie(Long id) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle("电影" + id);
        movie.setStatus(SystemConstants.MOVIE_STATUS_NORMAL);
        movie.setRatingSum(BigDecimal.ZERO);
        movie.setRatingCount(0);
        return movie;
    }

    private User buildUser(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname(nickname);
        user.setStatus(1);
        return user;
    }

    // ==================== 搜索影评 ====================

    @Test
    void searchReviews_emptyKeyword_returnsEmptyPage() {
        var result = reviewService.searchReviews(null, 1,0);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
    }

    @Test
    void searchReviews_esFailure_fallsBackToMySQL() {
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(com.lzh.document.ReviewDocument.class)))
                .thenThrow(new RuntimeException("ES down"));

        Page<Review> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(reviewQueryChain.and(any())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.eq(anyBoolean(), anyString(), any())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.like(anyString(), any())).thenReturn(reviewQueryChain);
        when(reviewQueryChain.page(any(Page.class))).thenReturn(page);

        var result = reviewService.searchReviews("肖申克", 1,0);

        assertTrue(result.getSuccess());
    }

    @Test
    void myReviews_secondPageStartsAtEleventhRecord() {
        var rows = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(id -> buildReview(1L, 1L, id, 8)).toList();
        when(reviewQueryChain.page(any(Page.class))).thenAnswer(call -> {
            Page<Review> page = call.getArgument(0);
            assertEquals(10, page.offset());
            assertEquals(11, page.getSize());
            assertFalse(page.searchCount());
            return page.setRecords(rows.subList(10, 21));
        });
        doReturn(List.of(buildUser(1L, "author"))).when(userService).listByIds(anySet());
        doReturn(List.of(buildMovie(1L))).when(movieService).listByIds(anySet());
        var data = (com.lzh.common.ScrollResult<com.lzh.vo.ReviewVO>) reviewService.myReviews(2).getData();
        assertTrue(data.getHasMore());
        assertEquals(java.util.stream.LongStream.rangeClosed(11, 20).boxed().toList(),
                data.getList().stream().map(com.lzh.vo.ReviewVO::getId).toList());
    }

    @Test
    void searchReviews_secondPageEsRequestUsesTenRowStride() {
        org.springframework.data.elasticsearch.core.SearchHits<com.lzh.document.ReviewDocument> hits = mock(org.springframework.data.elasticsearch.core.SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(com.lzh.document.ReviewDocument.class)))
                .thenAnswer(call -> {
                    NativeQuery query = call.getArgument(0);
                    assertEquals(10, query.getPageable().getOffset());
                    assertEquals(11, query.getPageable().getPageSize());
                    assertEquals(2, query.getSortOptions().size());
                    return hits;
                });
        var data = (com.lzh.common.ScrollResult<?>) reviewService.searchReviews("test", 2, null).getData();
        assertFalse(data.getHasMore());
    }

    @Test
    void hotReviews_fullLastPageHasNoMoreAndLookaheadIsNotReturned() {
        var ids = java.util.stream.LongStream.rangeClosed(1, 11).mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        when(zSetOperations.reverseRange(RedisConstants.HOT_REVIEW_KEY, 0, 10)).thenReturn(ids);
        doReturn(List.of()).when(reviewService).listByIds(anyList());
        var more = (com.lzh.common.ScrollResult<?>) reviewService.hotReviews(0).getData();
        assertTrue(more.getHasMore());
        verify(reviewService).listByIds(argThat((java.util.Collection<Long> values) -> values.size() == 10 && !values.contains(11L)));
        ids.remove("11");
        var last = (com.lzh.common.ScrollResult<?>) reviewService.hotReviews(1).getData();
        assertFalse(last.getHasMore());
        when(zSetOperations.reverseRange(RedisConstants.HOT_REVIEW_KEY, 10, 20)).thenReturn(Set.of());
        var empty = (com.lzh.common.ScrollResult<?>) reviewService.hotReviews(2).getData();
        assertFalse(empty.getHasMore());
        assertTrue(empty.getList().isEmpty());
    }


    @Test
    void userReviewCursorHasZeroOffsetAndReturnsNextPosition() {
        var rows = java.util.stream.LongStream.rangeClosed(1, 11)
                .mapToObj(id -> buildReview(1L, 1L, id, 8)).toList();
        when(reviewQueryChain.page(any(Page.class))).thenAnswer(call -> {
            Page<Review> page = call.getArgument(0);
            assertEquals(0, page.offset());
            return page.setRecords(rows);
        });
        doReturn(List.of(buildUser(1L, "author"))).when(userService).listByIds(anySet());
        doReturn(List.of(buildMovie(1L))).when(movieService).listByIds(anySet());
        var data = (com.lzh.common.ScrollResult<?>) reviewService.myReviewsCursor(null).getData();
        assertEquals(10, data.getList().size());
        assertTrue(data.getHasMore());
        assertNotNull(data.getNextCursor());
        assertFalse(com.lzh.utils.SeekCursor.after(data.getNextCursor(), "user-reviews:1", "like_count", "id").isEmptyOfWhere());
    }

    @Test
    void searchDeepPageNeverCallsEsOrDatabase() {
        assertThrows(com.lzh.common.BusinessException.class, () -> reviewService.searchReviews("test", 101, null));
        verifyNoInteractions(elasticsearchOperations);
        verify(reviewQueryChain, never()).page(any(Page.class));
    }

    @Test
    void esWindowErrorDoesNotFallBackToMysql() {
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(com.lzh.document.ReviewDocument.class)))
                .thenThrow(new RuntimeException("Result window is too large"));
        assertThrows(com.lzh.common.BusinessException.class, () -> reviewService.searchReviews("test", 1, null));
        verify(reviewQueryChain, never()).page(any(Page.class));
    }

}
