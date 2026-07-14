package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admins/users")
public class AdminUserController {

    @Resource
    private IAdminUserService adminUserService;

    @GetMapping("/list")
    public Result list(@RequestParam(value = "current",defaultValue = "1") Long  current){
          return adminUserService.list(current);
    }
    @GetMapping("/info")
    public Result info(@RequestParam(value = "userId") Long userId){
        return adminUserService.info(userId);
    }
}
