package com.lzh.cache.init;

import com.lzh.service.IMovieService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MovieCacheInit implements ApplicationRunner {
    @Resource
    private IMovieService movieService;
    @Override
    public void run(ApplicationArguments args){
        try {
            movieService.updateHotMovieCache();
        } catch (Exception e) {
            log.error("预热热门电影缓存失败");
        }
    }
}