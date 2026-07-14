package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminUserService;
import com.lzh.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admins/users")
public class AdminUserController {

    @Resource
    private IAdminUserService adminUserService;
    @Resource
    private IUserService userService;

    @GetMapping("/list")
    public Result list(@RequestParam(value = "current",defaultValue = "1") Long  current){
          return adminUserService.list(current);
    }
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long id){
        return userService.info(id);
    }
    @PutMapping("/status/{id}")
    public Result updateStatus(@PathVariable("id") Long id){
        return adminUserService.updateStatus(id);
    }
}
