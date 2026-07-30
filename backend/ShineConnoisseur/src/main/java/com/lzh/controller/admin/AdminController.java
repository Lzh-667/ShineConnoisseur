package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.dto.AdminLoginDTO;
import com.lzh.service.IAdminService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@Slf4j
@RestController
@RequestMapping("/admins")
@Tag(name = "管理端-认证", description = "管理员登录、退出")
public class AdminController {
    @Resource
    private IAdminService adminService;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result login(@RequestBody AdminLoginDTO adminLoginDTO){
        return adminService.login(adminLoginDTO);
    }
    @Operation(summary = "管理员退出")
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        String token = request.getHeader("authorization");
        return adminService.logout(token);
    }
}
