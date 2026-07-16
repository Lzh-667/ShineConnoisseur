package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.po.Movie;

public interface IMovieService extends IService<Movie> {

    Result getMovieInfo(Long movieId);

    Result listMovies(Long current, String name, String genre, String region);

    Result listHotMovies();

    void updateHotMovieCache();

    Result isFavorite(Long movieId);

    Result favoriteMovie(Long movieId, Boolean isFavorite);
}
