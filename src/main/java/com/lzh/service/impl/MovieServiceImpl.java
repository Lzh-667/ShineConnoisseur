package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.mapper.MovieMapper;
import com.lzh.po.Movie;
import com.lzh.service.IMovieService;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.vo.MovieSimpleVO;
import com.lzh.vo.MovieVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements IMovieService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result getMovieInfo(Long movieId) {
        //1.判断redis中是否存在
        String key = RedisConstants.MOVIE_INFO_KEY + movieId;
        String movieJson = stringRedisTemplate.opsForValue().get(key);
        if(movieJson != null){
            if(movieJson.isEmpty()){
                return Result.fail("电影不存在");
            }
            MovieVO movieVO = JSONUtil.toBean(movieJson, MovieVO.class);
            return Result.ok(movieVO);
        }
        //2.查数据库，判断电影是否存在
        Movie movie = getById(movieId);
        if (movie == null) {
            stringRedisTemplate.opsForValue().set(key, "", 5, TimeUnit.MINUTES);
            return Result.fail("电影不存在");
        }
        //3.转化为VO并返回
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
        //4.写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(movieVO), RedisConstants.MOVIE_INFO_TTL, TimeUnit.MINUTES);
        return Result.ok(movieVO);
    }

    @Override
    public Result listMovies(Long current, String title, String genre, String region) {
        //1.查询电影列表
        Page<Movie> page = query()
                .like(StrUtil.isNotBlank(title),"title", title)
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
    public Result listHotMovies() {
        String key = RedisConstants.HOT_MOVIE_KEY;
        //1.查Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.不存在,不符合正常情况，返回失败
        if(json == null){
            return Result.fail("服务器异常");
        }
        //3.返回热门电影
        return Result.ok(JSONUtil.toList(json, MovieSimpleVO.class));
    }
    @Override
    public void updateHotMovieCache(){
        //1. 查询热门电影
        List<Movie> movies = query()
                .orderByDesc("rating_count")
                .orderByDesc("rating_sum")
                .orderByDesc("release_date")
                .last("limit 10")
                .list();
        //2. 转VO
        List<MovieSimpleVO> vos = movies.stream()
                .map(movie -> BeanUtil.copyProperties(movie, MovieSimpleVO.class))
                .toList();
        //3. 写入Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.HOT_MOVIE_KEY, JSONUtil.toJsonStr(vos));
        log.info("热门电影缓存刷新成功");
    }
}