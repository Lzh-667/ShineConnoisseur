package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.mapper.MovieMapper;
import com.lzh.po.Movie;
import com.lzh.service.IMovieService;
import com.lzh.utils.SystemConstants;
import com.lzh.vo.MovieSimpleVO;
import com.lzh.vo.MovieVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
        //1.查询电影列表
        Page<Movie> page = query()
                .like(StrUtil.isNotBlank(name),"name", name)
                .eq(StrUtil.isNotBlank(genre),"genre", genre)
                .eq(StrUtil.isNotBlank(region),"region", region)
                .orderByDesc("release_date")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        //2.包装为movieSimpleVO
        List<MovieSimpleVO> records = page.getRecords().stream()
                .map(movie -> BeanUtil.copyProperties(movie, MovieSimpleVO.class))
                .toList();

        //3.封装为PageResult并返回
        PageResult<MovieSimpleVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(records);

        return Result.ok(result);
    }

    @Override
    public Result listHotMovies(Long current) {
        //1.查询电影列表
        Page<Movie> page = query()
                .orderByDesc("rating_count")
                .orderByDesc("rating_sum")
                .orderByDesc("release_date")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        //2.包装为movieSimpleVO
        List<MovieSimpleVO> records = page.getRecords().stream()
                .map(movie -> BeanUtil.copyProperties(movie, MovieSimpleVO.class))
                .toList();

        //3.封装为PageResult并返回
        PageResult<MovieSimpleVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(records);

        return Result.ok(result);
    }
}
