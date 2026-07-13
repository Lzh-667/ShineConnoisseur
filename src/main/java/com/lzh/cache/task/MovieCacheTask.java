package com.lzh.cache.task;

import com.lzh.service.IMovieService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MovieCacheTask {
    @Resource
    private IMovieService movieService;
    @Scheduled(cron = "0 */30 * * * ?")
    public void refreshHotMovie(){
        try {
            movieService.updateHotMovieCache();
        }catch(Exception e){
            log.error("刷新热门电影缓存失败",e);
        }
    }
}
