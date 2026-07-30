package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminUserService;
import com.lzh.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/admins/users")
@Tag(name = "管理端-用户", description = "用户列表、详情、封禁/解封")
public class AdminUserController {

    @Resource
    private IAdminUserService adminUserService;
    @Resource
    private IUserService userService;
    @Operation(summary = "用户列表")
    @GetMapping("/list")
    public Result list(@RequestParam(value = "current",defaultValue = "1") Integer current){
          return adminUserService.list(current);
    }
    @Operation(summary = "用户详情")
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long id){
        return userService.info(id);
    }
    @Operation(summary = "封禁/解封用户")
    @PutMapping("/status/{id}")
    public Result updateStatus(@PathVariable("id") Long id){
        return adminUserService.updateStatus(id);
    }
}