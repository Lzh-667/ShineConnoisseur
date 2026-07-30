package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.IAdminDashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/admins/dashboard")
@Tag(name = "管理端-看板", description = "数据看板统计")
public class AdminDashboardController {

    @Resource
    private IAdminDashboardService adminDashboardService;
    @Operation(summary = "看板数据")
    @GetMapping
    public Result dashboard(){
        return adminDashboardService.dashboard();
    }

}
