package com.lzh.cache.init;

import com.lzh.service.IReviewService;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReviewCacheInit implements ApplicationRunner {
    @Resource
    private IReviewService reviewService;
    @Override
    public void run(ApplicationArguments args){
        reviewService.updateHotReviewCache();
    }
}
