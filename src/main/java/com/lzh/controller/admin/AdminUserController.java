package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long id){
        return adminUserService.info(id);
    }
    @PutMapping("/status/{id}")
    public Result status(@PathVariable("id") Long id){
        return adminUserService.status(id);
    }
}
