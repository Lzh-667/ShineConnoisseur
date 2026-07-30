package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.po.Movie;

public interface IMovieService extends IService<Movie> {

    Result getMovieInfo(Long movieId);

    Result listHotMovies(Integer current);

    void updateHotMovieCache();

    Result isFavorite(Long movieId);

    Result favoriteMovie(Long movieId, Boolean isFavorite);

    Result listFavoriteMovies(Integer current);

    Result searchMovies(String keyword, Integer current, String genre, String region);
}
