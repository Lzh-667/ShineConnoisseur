package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.dto.LoginFormDTO;
import com.lzh.dto.RegisterFormDTO;
import com.lzh.dto.UpdatePasswordDTO;
import com.lzh.dto.UpdateProfileDTO;
import com.lzh.service.IUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    @Resource
    private IUserService userService;
    @PostMapping("/code")
    public Result sendLoginCode(@RequestParam("phone") String phone) {
        return userService.sendLoginCode(phone);
    }
    @PostMapping("/login/code")
    public Result loginByCode(@RequestBody LoginFormDTO loginForm) {
        return userService.loginByCode(loginForm);
    }
    @PostMapping("/login/password")
    public Result loginByPassword(@RequestBody LoginFormDTO loginForm) {
        return userService.loginByPassword(loginForm);
    }
    @PostMapping("/registerCode")
    public Result registerCode(@RequestParam("phone") String phone) {
        return userService.sendRegisterCode(phone);
    }
    @PostMapping("/register")
    public Result register(@RequestBody RegisterFormDTO registerFormDTO) {
        return userService.register(registerFormDTO);
    }
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long id) {
        return userService.info(id);
    }
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        return userService.logout(token);
    }
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody UpdateProfileDTO dto) {
        return userService.updateProfile(dto);
    }
    @PutMapping("/password")
    public Result updatePassword(@RequestBody UpdatePasswordDTO dto) {
        return userService.updatePassword(dto);
    }
}
