package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.dto.LoginFormDTO;
import com.lzh.dto.RegisterFormDTO;
import com.lzh.dto.ResetPasswordDTO;
import com.lzh.dto.UpdatePasswordDTO;
import com.lzh.dto.UpdateProfileDTO;

import com.lzh.service.IUserService;
import com.lzh.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/users")
@Tag(name = "用户模块", description = "注册、登录、资料管理、密码重置、账号注销")
public class UserController {

    @Resource
    private IUserService userService;
    @Operation(summary = "发送登录验证码")
    @PostMapping("/code")
    public Result sendLoginCode(@RequestParam("phone") String phone) {
        return userService.sendLoginCode(phone);
    }
    @Operation(summary = "验证码登录")
    @PostMapping("/login/code")
    public Result loginByCode(@RequestBody LoginFormDTO loginForm) {
        return userService.loginByCode(loginForm);
    }
    @Operation(summary = "密码登录")
    @PostMapping("/login/password")
    public Result loginByPassword(@RequestBody LoginFormDTO loginForm) {
        return userService.loginByPassword(loginForm);
    }
    @Operation(summary = "发送注册验证码")
    @PostMapping("/registerCode")
    public Result registerCode(@RequestParam("phone") String phone) {
        return userService.sendRegisterCode(phone);
    }
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result register(@RequestBody RegisterFormDTO registerFormDTO) {
        return userService.register(registerFormDTO);
    }
    @Operation(summary = "查看用户信息")
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long id) {
        return userService.info(id);
    }
    @Operation(summary = "查看个人信息")
    @GetMapping("/me")
    public Result me() {
        Long userId = UserHolder.getUser().getId();
        return userService.info(userId);
    }
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        return userService.logout(token);
    }
    @Operation(summary = "修改个人资料")
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody UpdateProfileDTO dto) {
        return userService.updateProfile(dto);
    }
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result updatePassword(@RequestBody UpdatePasswordDTO dto) {
        return userService.updatePassword(dto);
    }
    @Operation(summary = "发送重置密码验证码")
    @PostMapping("/resetCode")
    public Result sendResetCode(@RequestParam("phone") String phone) {
        return userService.sendResetCode(phone);
    }
    @Operation(summary = "重置密码")
    @PostMapping("/resetPassword")
    public Result resetPassword(@RequestBody ResetPasswordDTO dto) {
        return userService.resetPassword(dto);
    }
    @Operation(summary = "注销账号")
    @DeleteMapping("/account")
    public Result deleteAccount() {
        return userService.deleteAccount();
    }
}
