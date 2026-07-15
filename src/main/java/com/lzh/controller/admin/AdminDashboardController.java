package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminDashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admins/dashboard")
public class AdminDashboardController {

    @Resource
    private IAdminDashboardService adminDashboardService;
    @GetMapping
    public Result dashboard(){
        return adminDashboardService.dashboard();
    }

}
