package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.IMovieService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/movies")
@Tag(name = "电影模块", description = "电影详情、热门排行、收藏、搜索")
public class MovieController {

    @Resource
    private IMovieService movieService;

    @Operation(summary = "获取电影详情")
    @GetMapping("/{movieId}")
    public Result getMovieInfo(@PathVariable("movieId") Long movieId){
        return movieService.getMovieInfo(movieId);
    }
    @Operation(summary = "热门电影排行")
    @GetMapping("/hot")
    public Result listHotMovies(@RequestParam(value = "current",defaultValue = "1") Integer current){
        return movieService.listHotMovies(current);
    }
    @Operation(summary = "检查是否收藏")
    @GetMapping("/or/not/{movieId}")
    public Result isFavorite(@PathVariable("movieId") Long movieId){
        return movieService.isFavorite(movieId);
    }
    @Operation(summary = "收藏/取消收藏")
    @PostMapping("/favorite/{movieId}/{isFavorite}")
    public Result favoriteMovie(
            @PathVariable("movieId") Long movieId,
            @PathVariable("isFavorite") Boolean isFavorite
    ){
        return movieService.favoriteMovie(movieId,isFavorite);
    }
    @Operation(summary = "我的收藏列表")
    @GetMapping("/favorite")
    public Result listFavoriteMovies(@RequestParam("current") Integer current){
        return movieService.listFavoriteMovies(current);
    }

    @Operation(summary = "搜索电影")
    @GetMapping("/search")
    public Result searchMovies(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "region", required = false) String region
    ) {
        return movieService.searchMovies(keyword, current, genre, region);
    }

}
