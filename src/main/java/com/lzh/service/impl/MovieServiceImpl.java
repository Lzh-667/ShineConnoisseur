package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.Result;
import com.lzh.mapper.MovieMapper;
import com.lzh.po.Movie;
import com.lzh.service.IMovieService;
import com.lzh.vo.MovieVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements IMovieService {
    @Override
    public Result getMovieInfo(Long movieId) {
        //1.判断电影是否存在
        Movie movie = getById(movieId);
        if (movie == null) {
            return Result.fail("电影不存在");
        }
        //2.转化为VO并返回
        MovieVO movieVO = BeanUtil.copyProperties(movie, MovieVO.class);
        if(movie.getRatingCount()>0){
            BigDecimal rating = movie.getRatingSum()
                    .divide(
                            BigDecimal.valueOf(movie.getRatingCount()),
                            1,
                            RoundingMode.HALF_UP
                    );
            movieVO.setRating(rating);
        }else{
            movieVO.setRating(BigDecimal.ZERO);
        }
        return Result.ok(movieVO);
    }

    @Override
    public Result listMovies(Long current, String name, String genre, String region) {
        return null;
    }

    @Override
    public Result listHotMovies(Long current) {
        return null;
    }
}
