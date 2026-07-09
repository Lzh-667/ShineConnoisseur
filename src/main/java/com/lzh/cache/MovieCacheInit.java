package com.lzh.cache;

import com.lzh.service.IMovieService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class MovieCacheInit {
    @Resource
    private IMovieService movieService;
    @PostConstruct
    public void init(){
        movieService.updateHotMovieCache();
    }
}
