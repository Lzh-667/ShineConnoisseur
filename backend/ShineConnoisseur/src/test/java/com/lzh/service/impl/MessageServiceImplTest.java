package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.dto.UserDTO;
import com.lzh.po.Message;
import com.lzh.po.User;
import com.lzh.service.IUserService;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceImplTest {

    @Mock private IUserService userService;
    @Mock private QueryChainWrapper<Message> messageQueryChain;
    @Mock private LambdaUpdateChainWrapper<Message> lambdaUpdateChain;

    @Spy @InjectMocks
    private MessageServiceImpl messageService;

    private MockedStatic<UserHolder> userHolderMock;

    @BeforeEach
    void setUp() {
        var mockUser = new UserDTO();
        mockUser.setId(1L);
        userHolderMock = mockStatic(UserHolder.class);
        userHolderMock.when(UserHolder::getUser).thenReturn(mockUser);

        // message query chain
        doReturn(messageQueryChain).when(messageService).query();
        when(messageQueryChain.eq(anyBoolean(), anyString(), any())).thenReturn(messageQueryChain);
        when(messageQueryChain.eq(anyString(), any())).thenReturn(messageQueryChain);
        when(messageQueryChain.orderByDesc(anyString())).thenReturn(messageQueryChain);

        // lambda update chain — used by read() and readAll()
        doReturn(lambdaUpdateChain).when(messageService).lambdaUpdate();
        when(lambdaUpdateChain.eq(any(), any())).thenReturn(lambdaUpdateChain);
        when(lambdaUpdateChain.set(any(), any())).thenReturn(lambdaUpdateChain);
        when(lambdaUpdateChain.update()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    // ==================== listAll ====================

    @Test
    void listAll_withType_returnsList() {
        Message msg = buildMessage(1L, 2L, SystemConstants.MESSAGE_TYPE_LIKE_REVIEW);
        Page<Message> page = new Page<>(1, 10);
        page.setRecords(List.of(msg));
        page.setTotal(1);
        when(messageQueryChain.page(any(Page.class))).thenReturn(page);

        User fromUser = new User();
        fromUser.setId(2L);
        fromUser.setUsername("fromUser");
        fromUser.setNickname("发消息的人");
        when(userService.listByIds(anyList())).thenReturn(List.of(fromUser));

        var result = messageService.listAll(1, SystemConstants.MESSAGE_TYPE_LIKE_REVIEW);

        assertTrue(result.getSuccess());
    }

    @Test
    void listAll_noType_returnsAll() {
        Message msg = buildMessage(1L, 2L, SystemConstants.MESSAGE_TYPE_FOLLOW);
        Page<Message> page = new Page<>(1, 10);
        page.setRecords(List.of(msg));
        page.setTotal(1);
        when(messageQueryChain.page(any(Page.class))).thenReturn(page);

        User fromUser = new User();
        fromUser.setId(2L);
        fromUser.setUsername("fromUser");
        fromUser.setNickname("关注者");
        when(userService.listByIds(anyList())).thenReturn(List.of(fromUser));

        var result = messageService.listAll(1, null);

        assertTrue(result.getSuccess());
    }

    @Test
    void listAll_empty_returnsEmpty() {
        Page<Message> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        when(messageQueryChain.page(any(Page.class))).thenReturn(page);

        var result = messageService.listAll(1, null);

        assertTrue(result.getSuccess());
    }

    // ==================== unreadCount ====================

    @Test
    void unreadCount_hasUnread_returnsCount() {
        when(messageQueryChain.count()).thenReturn(5L);

        var result = messageService.unreadCount();

        assertTrue(result.getSuccess());
        assertEquals(5L, result.getData());
    }

    @Test
    void unreadCount_noUnread_returnsZero() {
        when(messageQueryChain.count()).thenReturn(0L);

        var result = messageService.unreadCount();

        assertTrue(result.getSuccess());
        assertEquals(0L, result.getData());
    }

    // ==================== read ====================

    @Test
    void read_success_returnsOk() {
        when(lambdaUpdateChain.update()).thenReturn(true);

        var result = messageService.read(1L);

        assertTrue(result.getSuccess());
    }

    @Test
    void read_notFound_returnsFail() {
        when(lambdaUpdateChain.update()).thenReturn(false);

        var result = messageService.read(1L);

        assertFalse(result.getSuccess());
        assertEquals("消息不存在或无权限", result.getErrorMsg());
    }

    // ==================== readAll ====================

    @Test
    void read_fail_noPermission() {
        when(lambdaUpdateChain.update()).thenReturn(false);

        var result = messageService.read(1L);

        assertFalse(result.getSuccess());
        assertEquals("消息不存在或无权限", result.getErrorMsg());
    }

    // ==================== helpers ====================

    private Message buildMessage(Long id, Long fromUserId, int type) {
        Message msg = new Message();
        msg.setId(id);
        msg.setUserId(1L);
        msg.setFromUserId(fromUserId);
        msg.setType(type);
        msg.setContent("测试消息");
        msg.setStatus(SystemConstants.MESSAGE_STATUS_UNREAD);
        return msg;
    }
}
