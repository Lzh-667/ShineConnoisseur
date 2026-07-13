package com.lzh.cache.task;

import com.lzh.service.IReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReviewCacheTask {
    @Resource
    private IReviewService reviewService;
    @Scheduled(cron = "0 0 6 * * ?")
    public void refreshHotReview(){
        try {
            reviewService.updateHotReviewCache();
        }catch(Exception e){
            log.error("刷新热门影评缓存失败",e);
        }
    }
}
