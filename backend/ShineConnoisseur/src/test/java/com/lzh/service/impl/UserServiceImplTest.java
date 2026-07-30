package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.lzh.dto.LoginFormDTO;
import com.lzh.dto.RegisterFormDTO;
import com.lzh.dto.UpdatePasswordDTO;
import com.lzh.dto.UpdateProfileDTO;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.UserMapper;
import com.lzh.po.User;
import com.lzh.utils.RedisConstants;
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
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private QueryChainWrapper<User> userQueryChain;
    @Mock private UpdateChainWrapper<User> userUpdateChain;
    @Mock private LambdaUpdateChainWrapper<User> userLambdaUpdateChain;

    @Spy @InjectMocks
    private UserServiceImpl userService;

    private MockedStatic<UserHolder> userHolderMock;
    private UserDTO mockUser;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        mockUser = new UserDTO();
        mockUser.setId(1L);
        mockUser.setNickname("testUser");
        mockUser.setUsername("testUser");
        userHolderMock = mockStatic(UserHolder.class);
        userHolderMock.when(UserHolder::getUser).thenReturn(mockUser);

        // query chain
        doReturn(userQueryChain).when(userService).query();
        when(userQueryChain.eq(any(), any())).thenReturn(userQueryChain);

        // update chain
        doReturn(userUpdateChain).when(userService).update();
        when(userUpdateChain.setSql(anyString())).thenReturn(userUpdateChain);
        when(userUpdateChain.eq(any(), any())).thenReturn(userUpdateChain);
        when(userUpdateChain.gt(anyString(), anyInt())).thenReturn(userUpdateChain);

        // lambda update chain
        doReturn(userLambdaUpdateChain).when(userService).lambdaUpdate();
        doReturn(userLambdaUpdateChain).when(userLambdaUpdateChain).eq(any(), any());
        doReturn(userLambdaUpdateChain).when(userLambdaUpdateChain).set(anyBoolean(), any(), any());
        doReturn(userLambdaUpdateChain).when(userLambdaUpdateChain).set(any(), any());
        when(userLambdaUpdateChain.update()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    // ==================== sendLoginCode ====================

    @Test
    void sendLoginCode_invalidPhone_returnsFail() {
        var result = userService.sendLoginCode("123");
        assertFalse(result.getSuccess());
        assertEquals("手机号格式错误", result.getErrorMsg());
    }

    @Test
    void sendLoginCode_frequentSend_returnsFail() {
        String codeKey = RedisConstants.LOGIN_CODE_KEY + "13800138000";
        when(valueOperations.get(codeKey)).thenReturn("123456");

        var result = userService.sendLoginCode("13800138000");

        assertFalse(result.getSuccess());
        assertEquals("发送过于频繁，请稍后再试", result.getErrorMsg());
    }

    @Test
    void sendLoginCode_userNotFound_returnsOk() {
        String codeKey = RedisConstants.LOGIN_CODE_KEY + "13800138000";
        when(valueOperations.get(codeKey)).thenReturn(null);
        when(userQueryChain.one()).thenReturn(null);

        var result = userService.sendLoginCode("13800138000");

        assertTrue(result.getSuccess());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void sendLoginCode_userExists_sendsCode() {
        String codeKey = RedisConstants.LOGIN_CODE_KEY + "13800138000";
        when(valueOperations.get(codeKey)).thenReturn(null);
        User user = buildUser(1L, "13800138000");
        when(userQueryChain.one()).thenReturn(user);

        var result = userService.sendLoginCode("13800138000");

        assertTrue(result.getSuccess());
        verify(valueOperations).set(eq(codeKey), anyString(), eq(RedisConstants.LOGIN_CODE_TTL), eq(TimeUnit.MINUTES));
    }

    // ==================== loginByCode ====================

    @Test
    void loginByCode_invalidPhone_returnsFail() {
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone("123");
        form.setCode("123456");

        var result = userService.loginByCode(form);

        assertFalse(result.getSuccess());
    }

    @Test
    void loginByCode_tooManyErrors_returnsFail() {
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone("13800138000");
        form.setCode("123456");
        String errorKey = RedisConstants.LOGIN_USER_CODE_ERR_KEY + "13800138000";
        when(valueOperations.get(errorKey)).thenReturn("5");

        var result = userService.loginByCode(form);

        assertFalse(result.getSuccess());
        assertEquals("错误次数过多，请稍后再试", result.getErrorMsg());
    }

    @Test
    void loginByCode_wrongCode_incrementsError() {
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone("13800138000");
        form.setCode("123456");
        String errorKey = RedisConstants.LOGIN_USER_CODE_ERR_KEY + "13800138000";
        String codeKey = RedisConstants.LOGIN_CODE_KEY + "13800138000";
        when(valueOperations.get(errorKey)).thenReturn("2");
        when(valueOperations.get(codeKey)).thenReturn("654321");
        when(valueOperations.increment(errorKey)).thenReturn(3L);

        var result = userService.loginByCode(form);

        assertFalse(result.getSuccess());
        assertEquals("验证码错误", result.getErrorMsg());
    }

    @Test
    void loginByCode_success_returnsToken() {
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone("13800138000");
        form.setCode("123456");
        String errorKey = RedisConstants.LOGIN_USER_CODE_ERR_KEY + "13800138000";
        String codeKey = RedisConstants.LOGIN_CODE_KEY + "13800138000";
        when(valueOperations.get(errorKey)).thenReturn(null);
        when(valueOperations.get(codeKey)).thenReturn("123456");
        when(stringRedisTemplate.delete(codeKey)).thenReturn(true);
        when(stringRedisTemplate.delete(errorKey)).thenReturn(true);

        User user = buildUser(1L, "13800138000");
        when(userQueryChain.one()).thenReturn(user);

        doNothing().when(hashOperations).putAll(anyString(), anyMap());
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        var result = userService.loginByCode(form);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
    }

    // ==================== loginByPassword ====================

    @Test
    void loginByPassword_emptyUsername_returnsFail() {
        LoginFormDTO form = new LoginFormDTO();
        form.setUsername("");
        form.setPassword("Pass123456");

        var result = userService.loginByPassword(form);

        assertFalse(result.getSuccess());
        assertEquals("用户名不能为空", result.getErrorMsg());
    }

    @Test
    void loginByPassword_invalidPassword_returnsFail() {
        LoginFormDTO form = new LoginFormDTO();
        form.setUsername("testUser");
        form.setPassword("123"); // too short

        var result = userService.loginByPassword(form);

        assertFalse(result.getSuccess());
        assertEquals("密码格式错误", result.getErrorMsg());
    }

    @Test
    void loginByPassword_wrongPassword_incrementsError() {
        LoginFormDTO form = new LoginFormDTO();
        form.setUsername("testUser");
        form.setPassword("Pass123456");
        String errorKey = RedisConstants.LOGIN_USER_PASSWORD_ERR_KEY + "testUser";
        when(valueOperations.get(errorKey)).thenReturn("2");

        User user = buildUser(1L, "13800138000");
        when(userQueryChain.one()).thenReturn(user);
        when(passwordEncoder.matches("Pass123456", user.getPassword())).thenReturn(false);
        when(valueOperations.increment(errorKey)).thenReturn(3L);

        var result = userService.loginByPassword(form);

        assertFalse(result.getSuccess());
    }

    @Test
    void loginByPassword_success_returnsToken() {
        LoginFormDTO form = new LoginFormDTO();
        form.setUsername("testUser");
        form.setPassword("Pass123456");
        String errorKey = RedisConstants.LOGIN_USER_PASSWORD_ERR_KEY + "testUser";
        when(valueOperations.get(errorKey)).thenReturn(null);

        User user = buildUser(1L, "13800138000");
        when(userQueryChain.one()).thenReturn(user);
        when(passwordEncoder.matches("Pass123456", user.getPassword())).thenReturn(true);
        when(stringRedisTemplate.delete(errorKey)).thenReturn(true);
        doNothing().when(hashOperations).putAll(anyString(), anyMap());
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        var result = userService.loginByPassword(form);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
    }

    // ==================== register ====================

    @Test
    void register_passwordMismatch_returnsFail() {
        RegisterFormDTO form = buildRegisterForm("newUser", "Pass123456", "Pass654321", "13800138001", null);

        var result = userService.register(form);

        assertFalse(result.getSuccess());
        assertEquals("两次输入密码不一致", result.getErrorMsg());
    }

    @Test
    void register_usernameExists_returnsFail() {
        RegisterFormDTO form = buildRegisterForm("existingUser", "Pass123456", "Pass123456", "13800138001", null);

        // first query for username
        when(userQueryChain.one()).thenReturn(buildUser(2L, "13800000000"));

        var result = userService.register(form);

        assertFalse(result.getSuccess());
        assertEquals("用户名已存在", result.getErrorMsg());
    }

    @Test
    void register_wrongCode_returnsFail() {
        RegisterFormDTO form = buildRegisterForm("newUser", "Pass123456", "Pass123456", "13800138001", null);
        // username not exist, phone not exist
        when(userQueryChain.one()).thenReturn(null).thenReturn(null);
        String codeKey = RedisConstants.REGISTER_CODE_KEY + "13800138001";
        when(valueOperations.get(codeKey)).thenReturn("654321");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        var result = userService.register(form);

        assertFalse(result.getSuccess());
        assertEquals("验证码错误", result.getErrorMsg());
    }

    @Test
    void register_success_returnsToken() {
        RegisterFormDTO form = buildRegisterForm("newUser", "Pass123456", "Pass123456", "13800138001", null);
        // username not exist, phone not exist
        when(userQueryChain.one()).thenReturn(null).thenReturn(null);
        String codeKey = RedisConstants.REGISTER_CODE_KEY + "13800138001";
        when(valueOperations.get(codeKey)).thenReturn("123456");
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);
        when(passwordEncoder.encode("Pass123456")).thenReturn("$2a$10$encoded");
        doReturn(true).when(userService).save(any(User.class));
        doNothing().when(hashOperations).putAll(anyString(), anyMap());
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        var result = userService.register(form);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
    }

    // ==================== updateProfile ====================

    @Test
    void updateProfile_allEmpty_returnsFail() {
        UpdateProfileDTO dto = new UpdateProfileDTO();

        var result = userService.updateProfile(dto);

        assertFalse(result.getSuccess());
        assertEquals("至少填写一项", result.getErrorMsg());
    }

    @Test
    void updateProfile_success_returnsOk() {
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("新昵称");
        dto.setGender(1);

        when(userLambdaUpdateChain.update()).thenReturn(true);

        var result = userService.updateProfile(dto);

        assertTrue(result.getSuccess());
    }

    // ==================== updatePassword ====================

    @Test
    void updatePassword_wrongOldPassword_returnsFail() {
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("wrongOld");
        dto.setNewPassword("NewPass123");

        User user = buildUser(1L, "13800138000");
        user.setPassword("$2a$10$encodedOld");
        doReturn(user).when(userService).getById(1L);
        when(passwordEncoder.matches("wrongOld", "$2a$10$encodedOld")).thenReturn(false);

        var result = userService.updatePassword(dto);

        assertFalse(result.getSuccess());
        assertEquals("原密码错误", result.getErrorMsg());
    }

    @Test
    void updatePassword_sameAsOld_returnsFail() {
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("Pass123456");
        dto.setNewPassword("Pass123456");

        var result = userService.updatePassword(dto);

        assertFalse(result.getSuccess());
        assertEquals("新密码不能与原密码相同", result.getErrorMsg());
    }

    @Test
    void updatePassword_success_returnsOk() {
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("OldPass123");
        dto.setNewPassword("NewPass456");

        User user = buildUser(1L, "13800138000");
        user.setPassword("$2a$10$encodedOld");
        doReturn(user).when(userService).getById(1L);
        when(passwordEncoder.matches("OldPass123", "$2a$10$encodedOld")).thenReturn(true);
        when(passwordEncoder.encode("NewPass456")).thenReturn("$2a$10$encodedNew");

        when(userLambdaUpdateChain.update()).thenReturn(true);

        var result = userService.updatePassword(dto);

        assertTrue(result.getSuccess());
    }

    // ==================== helpers ====================

    private User buildUser(Long id, String phone) {
        User user = new User();
        user.setId(id);
        user.setUsername("testUser");
        user.setPassword("$2a$10$encoded");
        user.setPhone(phone);
        user.setNickname("testUser" + id);
        user.setStatus(1);
        return user;
    }

    private RegisterFormDTO buildRegisterForm(String username, String password, String confirmPassword, String phone, String email) {
        RegisterFormDTO form = new RegisterFormDTO();
        form.setUsername(username);
        form.setPassword(password);
        form.setConfirmPassword(confirmPassword);
        form.setPhone(phone);
        form.setEmail(email);
        form.setCode("123456");
        return form;
    }
}
