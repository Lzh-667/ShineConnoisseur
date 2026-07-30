package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.dto.ReviewCommentDTO;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.po.LikeRecord;
import com.lzh.po.Review;
import com.lzh.po.ReviewComment;
import com.lzh.po.User;
import com.lzh.service.ILikeRecordService;
import com.lzh.service.IReviewService;
import com.lzh.service.IUserService;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewCommentServiceImplTest {

    @Mock private ILikeRecordService likeRecordService;
    @Mock private IReviewService reviewService;
    @Mock private IUserService userService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ReviewCommentMapper reviewCommentMapper;

    @Mock private QueryChainWrapper<ReviewComment> commentQueryChain;
    @Mock private UpdateChainWrapper<Review> reviewUpdateChain;
    @Mock private UpdateChainWrapper<ReviewComment> commentUpdateChain;
    @Mock private QueryChainWrapper<LikeRecord> likeRecordQueryChain;

    @Spy @InjectMocks
    private ReviewCommentServiceImpl commentService;

    private MockedStatic<UserHolder> userHolderMock;
    private UserDTO mockUser;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        mockUser = new UserDTO();
        mockUser.setId(1L);
        mockUser.setNickname("testUser");
        userHolderMock = mockStatic(UserHolder.class);
        userHolderMock.when(UserHolder::getUser).thenReturn(mockUser);

        // comment query chain
        doReturn(commentQueryChain).when(commentService).query();
        when(commentQueryChain.eq(anyString(), any())).thenReturn(commentQueryChain);
        when(commentQueryChain.ne(anyString(), any())).thenReturn(commentQueryChain);
        when(commentQueryChain.in(anyString(), anySet())).thenReturn(commentQueryChain);
        when(commentQueryChain.orderByDesc(anyString())).thenReturn(commentQueryChain);
        when(commentQueryChain.select(anyString())).thenReturn(commentQueryChain);
        when(commentQueryChain.list()).thenReturn(Collections.emptyList());
        when(commentQueryChain.one()).thenReturn(null);

        // comment update chain
        doReturn(commentUpdateChain).when(commentService).update();
        when(commentUpdateChain.setSql(anyString())).thenReturn(commentUpdateChain);
        when(commentUpdateChain.eq(anyString(), any())).thenReturn(commentUpdateChain);
        when(commentUpdateChain.gt(anyString(), anyInt())).thenReturn(commentUpdateChain);
        when(commentUpdateChain.update()).thenReturn(true);

        // reviewService.update() chain
        when(reviewService.update()).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.setSql(anyString())).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.eq(any(), any())).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.gt(anyString(), anyInt())).thenReturn(reviewUpdateChain);
        when(reviewUpdateChain.update()).thenReturn(true);

        // likeRecordService
        when(likeRecordService.query()).thenReturn(likeRecordQueryChain);
        when(likeRecordQueryChain.eq(anyString(), any())).thenReturn(likeRecordQueryChain);
        when(likeRecordQueryChain.in(anyString(), anySet())).thenReturn(likeRecordQueryChain);
        when(likeRecordQueryChain.list()).thenReturn(Collections.emptyList());

        // mapper stubs
        when(reviewCommentMapper.selectMaps(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        doReturn(reviewCommentMapper).when(commentService).getBaseMapper();
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    // ==================== publishReviewComment ====================

    @Test
    void publishReviewComment_rootComment_success() {
        ReviewCommentDTO dto = new ReviewCommentDTO();
        dto.setContent("好电影！");
        dto.setRootId(0L);
        dto.setReplyUserId(0L);

        doReturn(true).when(commentService).save(any(ReviewComment.class));
        doReturn(true).when(commentService).updateById(any(ReviewComment.class));

        Review review = buildReview(2L);
        when(reviewService.getById(1L)).thenReturn(review);
        User author = buildUser(1L);
        when(userService.getById(1L)).thenReturn(author);

        var result = commentService.publishReviewComment(dto, 1L);

        assertTrue(result.getSuccess());
        verify(commentService).save(any(ReviewComment.class));
    }

    @Test
    void publishReviewComment_replyToComment_success() {
        ReviewCommentDTO dto = new ReviewCommentDTO();
        dto.setContent("说得好");
        dto.setRootId(10L);
        dto.setReplyUserId(2L);

        ReviewComment rootComment = buildComment(10L, 2L, 1L, 0L);
        doReturn(rootComment).when(commentService).getById(10L);

        doReturn(true).when(commentService).save(any(ReviewComment.class));
        Review review = buildReview(2L);
        when(reviewService.getById(1L)).thenReturn(review);
        User author1 = buildUser(1L); // current user
        User author2 = buildUser(2L); // reply target
        when(userService.getById(1L)).thenReturn(author1);
        when(userService.getById(2L)).thenReturn(author2);

        var result = commentService.publishReviewComment(dto, 1L);

        assertTrue(result.getSuccess());
        verify(rabbitTemplate).convertAndSend(eq(MQConstants.MESSAGE_EXCHANGE), anyString(), any(Object.class));
    }

    @Test
    void publishReviewComment_rootCommentNotFound_returnsFail() {
        ReviewCommentDTO dto = new ReviewCommentDTO();
        dto.setContent("回复");
        dto.setRootId(999L);
        dto.setReplyUserId(2L);

        doReturn(null).when(commentService).getById(999L);

        var result = commentService.publishReviewComment(dto, 1L);

        assertFalse(result.getSuccess());
        assertEquals("评论不存在", result.getErrorMsg());
    }

    // ==================== likeReviewComment ====================

    @Test
    void likeReviewComment_commentNotFound_returnsFail() {
        String key = RedisConstants.LIKE_COMMENT_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(false);
        doReturn(false).when(commentService).exists(any(QueryWrapper.class));

        var result = commentService.likeReviewComment(1L);

        assertFalse(result.getSuccess());
    }

    @Test
    void likeReviewComment_unlike_success() {
        String key = RedisConstants.LIKE_COMMENT_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(true);
        when(setOperations.isMember(key, "1")).thenReturn(true);
        doReturn(true).when(likeRecordService).remove(any(QueryWrapper.class));
        ReviewComment comment = buildComment(1L, 2L, 1L, 0L);
        comment.setLikeCount(3);
        doReturn(comment).when(commentService).getById(1L);
        when(setOperations.remove(key, "1")).thenReturn(1L);

        var result = commentService.likeReviewComment(1L);

        assertTrue(result.getSuccess());
    }

    @Test
    void likeReviewComment_like_success() {
        String key = RedisConstants.LIKE_COMMENT_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(false);
        doReturn(true).when(commentService).exists(any(QueryWrapper.class));
        when(likeRecordQueryChain.exists()).thenReturn(false);
        doReturn(true).when(likeRecordService).save(any(LikeRecord.class));

        ReviewComment comment = buildComment(1L, 2L, 1L, 0L);
        comment.setLikeCount(3);
        doReturn(comment).when(commentService).getById(1L);
        when(setOperations.add(anyString(), anyString())).thenReturn(1L);

        when(commentQueryChain.select("user_id")).thenReturn(commentQueryChain);
        when(commentQueryChain.one()).thenReturn(comment);
        when(userService.getById(1L)).thenReturn(buildUser(1L));

        var result = commentService.likeReviewComment(1L);

        assertTrue(result.getSuccess());
    }

    // ==================== deleteReviewComment ====================

    @Test
    void deleteReviewComment_notOwner_returnsFail() {
        ReviewComment comment = buildComment(1L, 2L, 1L, 0L); // userId=2 ≠ current=1
        doReturn(comment).when(commentService).getById(1L);

        var result = commentService.deleteReviewComment(1L);

        assertFalse(result.getSuccess());
    }

    @Test
    void deleteReviewComment_leafComment_success() {
        ReviewComment comment = buildComment(1L, 1L, 1L, 0L);
        comment.setReplyUserId(2L); // not root (replyUserId != 0) → leaf reply
        doReturn(comment).when(commentService).getById(1L);
        doReturn(true).when(commentService).removeById(1L);
        doReturn(true).when(likeRecordService).remove(any(QueryWrapper.class));
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        var result = commentService.deleteReviewComment(1L);

        assertTrue(result.getSuccess());
    }

    @Test
    void deleteReviewComment_rootComment_cascadesChildren() {
        ReviewComment rootComment = buildComment(1L, 1L, 1L, 0L);
        rootComment.setReplyUserId(0L); // root comment
        doReturn(rootComment).when(commentService).getById(1L);
        doReturn(true).when(commentService).removeById(1L);
        doReturn(true).when(likeRecordService).remove(any(QueryWrapper.class));

        ReviewComment child = buildComment(2L, 3L, 1L, 1L);
        when(commentQueryChain.list()).thenReturn(List.of(child));

        when(reviewCommentMapper.delete(any(QueryWrapper.class))).thenReturn(1);
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        var result = commentService.deleteReviewComment(1L);

        assertTrue(result.getSuccess());
    }

    // ==================== helpers ====================

    private ReviewComment buildComment(Long id, Long userId, Long reviewId, Long rootId) {
        ReviewComment comment = new ReviewComment();
        comment.setId(id);
        comment.setUserId(userId);
        comment.setReviewId(reviewId);
        comment.setRootId(rootId);
        comment.setReplyUserId(0L);
        comment.setContent("评论内容");
        comment.setLikeCount(0);
        comment.setStatus(SystemConstants.COMMENT_STATUS_NORMAL);
        comment.setCreateTime(LocalDateTime.now());
        return comment;
    }

    private Review buildReview(Long userId) {
        Review review = new Review();
        review.setId(1L);
        review.setUserId(userId);
        review.setMovieId(1L);
        review.setRating(8);
        review.setStatus(SystemConstants.REVIEW_STATUS_NORMAL);
        return review;
    }

    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname("nick" + id);
        return user;
    }
}
