package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.dto.UserDTO;
import com.lzh.po.User;
import com.lzh.po.UserFollow;
import com.lzh.service.IUserService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowServiceImplTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private IUserService userService;
    @Mock private RabbitTemplate rabbitTemplate;

    @Mock private QueryChainWrapper<UserFollow> followQueryChain;
    @Mock private UpdateChainWrapper<User> userUpdateChain;

    @Spy @InjectMocks
    private FollowServiceImpl followService;

    private MockedStatic<UserHolder> userHolderMock;
    private UserDTO mockUser;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        mockUser = new UserDTO();
        mockUser.setId(1L);
        mockUser.setNickname("testUser");
        userHolderMock = mockStatic(UserHolder.class);
        userHolderMock.when(UserHolder::getUser).thenReturn(mockUser);

        doReturn(followQueryChain).when(followService).query();
        when(followQueryChain.eq(anyString(), any())).thenReturn(followQueryChain);
        when(followQueryChain.orderByDesc(anyString())).thenReturn(followQueryChain);
        when(followQueryChain.list()).thenReturn(Collections.emptyList());
        when(followQueryChain.exists()).thenReturn(false);

        when(userService.update()).thenReturn(userUpdateChain);
        when(userUpdateChain.setSql(anyString())).thenReturn(userUpdateChain);
        when(userUpdateChain.eq(anyString(), any())).thenReturn(userUpdateChain);
        when(userUpdateChain.gt(anyString(), anyInt())).thenReturn(userUpdateChain);
        when(userUpdateChain.update()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    // ==================== follow ====================

    @Test
    void follow_self_returnsFail() {
        var result = followService.follow(1L, true);

        assertFalse(result.getSuccess());
        assertEquals("不能关注自己", result.getErrorMsg());
    }

    @Test
    void follow_userNotExists_returnsFail() {
        doReturn(false).when(userService).exists(any(QueryWrapper.class));

        var result = followService.follow(2L, true);

        assertFalse(result.getSuccess());
        assertEquals("用户不存在", result.getErrorMsg());
    }

    @Test
    void follow_duplicate_returnsFail() {
        doReturn(true).when(userService).exists(any(QueryWrapper.class));
        when(followQueryChain.exists()).thenReturn(true);

        var result = followService.follow(2L, true);

        assertFalse(result.getSuccess());
        assertEquals("不能重复关注", result.getErrorMsg());
    }

    @Test
    void follow_success() {
        doReturn(true).when(userService).exists(any(QueryWrapper.class));
        doReturn(true).when(followService).save(any(UserFollow.class));
        User mockUser2 = buildUser(1L);
        User mockFollowed = buildUser(2L);
        doReturn(mockUser2).when(userService).getById(1L);
        doReturn(mockFollowed).when(userService).getById(2L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        var result = followService.follow(2L, true);

        assertTrue(result.getSuccess());
        verify(followService).save(any(UserFollow.class));
    }

    @Test
    void unfollow_success() {
        doReturn(true).when(followService).remove(any(QueryWrapper.class));
        when(zSetOperations.remove(anyString(), any())).thenReturn(1L);

        var result = followService.follow(2L, false);

        assertTrue(result.getSuccess());
    }

    // ==================== isFollow ====================

    @Test
    void isFollow_redisHit_isFollowed_returnsTrue() {
        String key = RedisConstants.FOLLOWING_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(true);
        when(zSetOperations.score(key, "2")).thenReturn(1.0);

        var result = followService.isFollow(2L);

        assertTrue(result.getSuccess());
        assertTrue((Boolean) result.getData());
    }

    @Test
    void isFollow_redisHit_notFollowed_returnsFalse() {
        String key = RedisConstants.FOLLOWING_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(true);
        when(zSetOperations.score(key, "2")).thenReturn(null);

        var result = followService.isFollow(2L);

        assertTrue(result.getSuccess());
        assertFalse((Boolean) result.getData());
    }

    @Test
    void isFollow_redisMiss_rebuildsFromDB() {
        String key = RedisConstants.FOLLOWING_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(false);
        UserFollow uf = new UserFollow();
        uf.setUserId(1L);
        uf.setFollowUserId(2L);
        uf.setCreateTime(LocalDateTime.now());
        when(followQueryChain.list()).thenReturn(List.of(uf));
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        var result = followService.isFollow(2L);

        assertTrue(result.getSuccess());
        assertTrue((Boolean) result.getData());
    }

    // ==================== getFollowerList ====================

    @Test
    void getFollowerList_redisHit_returnsList() {
        String key = RedisConstants.FOLLOWER_KEY + 1L;
        when(zSetOperations.size(key)).thenReturn(2L);
        when(zSetOperations.reverseRange(eq(key), anyLong(), anyLong())).thenReturn(Set.of("3", "4"));
        when(userService.listByIds(anyList())).thenReturn(List.of(buildUser(3L), buildUser(4L)));

        var result = followService.getFollowerList(1);

        assertTrue(result.getSuccess());
    }

    @Test
    void getFollowingList_dbHit_returnsList() {
        String key = RedisConstants.FOLLOWING_KEY + 1L;
        when(zSetOperations.size(key)).thenReturn(0L);

        Page<UserFollow> page = new Page<>(1, 10);
        page.setRecords(List.of(buildUserFollow(1L, 2L), buildUserFollow(1L, 3L)));
        page.setTotal(2);
        when(followQueryChain.page(any(Page.class))).thenReturn(page);

        // the cache rebuild also calls .list()
        when(followQueryChain.list()).thenReturn(List.of(buildUserFollow(1L, 2L), buildUserFollow(1L, 3L)));

        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(userService.listByIds(anyList())).thenReturn(List.of(buildUser(2L), buildUser(3L)));

        var result = followService.getFollowingList(1);

        assertTrue(result.getSuccess());
    }

    // ==================== helpers ====================

    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname("nick" + id);
        user.setStatus(SystemConstants.USER_STATUS_NORMAL);
        return user;
    }

    private UserFollow buildUserFollow(Long userId, Long followUserId) {
        UserFollow uf = new UserFollow();
        uf.setUserId(userId);
        uf.setFollowUserId(followUserId);
        uf.setCreateTime(LocalDateTime.now());
        return uf;
    }
}
