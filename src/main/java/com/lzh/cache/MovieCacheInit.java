package com.lzh.cache;

import com.lzh.service.IMovieService;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MovieCacheInit implements ApplicationRunner {
    @Resource
    private IMovieService movieService;
    @Override
    public void run(ApplicationArguments args){
        movieService.updateHotMovieCache();
    }
}