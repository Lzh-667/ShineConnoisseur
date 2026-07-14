package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.dto.AdminLoginDTO;
import com.lzh.service.IAdminService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/admins")
public class AdminController {
    @Resource
    private IAdminService adminService;

    @PostMapping("/login")
    public Result login(@RequestBody AdminLoginDTO adminLoginDTO){
        return adminService.login(adminLoginDTO);
    }
    @PostMapping("/logout")
    public Result logout(){
        return adminService.logout();
    }
}
