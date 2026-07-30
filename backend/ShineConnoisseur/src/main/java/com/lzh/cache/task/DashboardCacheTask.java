package com.lzh.cache.task;

import com.lzh.service.IAdminDashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DashboardCacheTask {
    @Resource
    private IAdminDashboardService adminDashboardService;
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshDashboardCache(){
        try {
            adminDashboardService.refreshDashboardCache();
        }catch(Exception e){
            log.error("刷新dashboard数据缓存失败",e);
        }
    }
}
