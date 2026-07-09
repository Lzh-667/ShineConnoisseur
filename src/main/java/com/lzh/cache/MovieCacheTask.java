package com.lzh.cache;

import com.lzh.service.IMovieService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MovieCacheTask {
    @Resource
    private IMovieService movieService;
    @Scheduled(cron = "0 */30 * * * ?")
    public void refreshHotMovie(){
        movieService.updateHotMovieCache();
    }
}
