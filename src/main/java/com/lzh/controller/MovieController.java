package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.IMovieService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/movies")
public class MovieController {

    @Resource
    private IMovieService movieService;

    @GetMapping("/{movieId}")
    public Result getMovieInfo(@PathVariable("movieId") Long movieId){
        return movieService.getMovieInfo(movieId);
    }
    @GetMapping("/list")
    public Result listMovies(
            @RequestParam(value = "current",defaultValue = "1") Long current,
            @RequestParam(value = "title",required = false) String title,
            @RequestParam(value = "genre",required = false) String genre,
            @RequestParam(value = "region",required = false) String region
    ){
        return movieService.listMovies(current,title,genre,region);
    }
    @GetMapping("/hot")
    public Result listHotMovies(){
        return movieService.listHotMovies();
    }
    @GetMapping("/or/not/{movieId}")
    public Result isFavorite(@PathVariable("movieId") Long movieId){
        return movieService.isFavorite(movieId);
    }
    @PostMapping("/favorite/{movieId}/{isFavorite}")
    public Result favoriteMovie(
            @PathVariable("movieId") Long movieId,
            @PathVariable("isFavorite") Boolean isFavorite
    ){
        return movieService.favoriteMovie(movieId,isFavorite);
    }

}
