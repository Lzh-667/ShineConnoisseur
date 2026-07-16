package com.lzh.cache.init;

import com.lzh.service.IReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReviewCacheInit implements ApplicationRunner {
    @Resource
    private IReviewService reviewService;
    @Override
    public void run(ApplicationArguments args){
        try {
            reviewService.updateHotReviewCache();
        } catch (Exception e) {
            log.error("预热热门影评缓存失败",e);
        }
    }
}
