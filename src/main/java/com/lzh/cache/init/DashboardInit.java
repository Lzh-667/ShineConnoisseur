package com.lzh.cache.init;

import com.lzh.service.IAdminDashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DashboardInit implements ApplicationRunner {
    @Resource
    private IAdminDashboardService adminDashboardService;
    @Override
    public void run(ApplicationArguments args){
        try {
            adminDashboardService.refreshDashboardCache();
        } catch (Exception e) {
            log.error("预热dashboard数据缓存失败");
        }
    }
}
