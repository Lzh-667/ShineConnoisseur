package com.lzh.service;

import com.lzh.common.Result;
import com.lzh.dto.AdminMovieDTO;

public interface IAdminMovieService {
    Result publishMovie(AdminMovieDTO movieDTO);

    Result updateMovie(AdminMovieDTO movieDTO,Long id);

    Result listMovies(Long current);

    Result updateMovieStatus(Long id);
}
