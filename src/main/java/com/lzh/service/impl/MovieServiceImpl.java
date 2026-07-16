package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.mapper.MovieMapper;
import com.lzh.po.Movie;
import com.lzh.po.MovieFavorite;
import com.lzh.service.IMovieFavoriteService;
import com.lzh.service.IMovieService;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.MovieSimpleVO;
import com.lzh.vo.MovieVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements IMovieService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IMovieFavoriteService movieFavoriteService;
    @Override
    public Result getMovieInfo(Long movieId){
        //1.判断redis中是否存在
        String key = RedisConstants.MOVIE_INFO_KEY + movieId;
        String movieJson = stringRedisTemplate.opsForValue().get(key);
        if(movieJson != null){
            if("empty".equals(movieJson)){
                return Result.fail("电影不存在");
            }
            MovieVO movieVO = JSONUtil.toBean(movieJson, MovieVO.class);
            return Result.ok(movieVO);
        }
        //2.查数据库，判断电影是否存在
        Movie movie = getById(movieId);
        if (movie == null||!Objects.equals(movie.getStatus(), SystemConstants.MOVIE_STATUS_NORMAL)) {
            stringRedisTemplate.opsForValue().set(key, "empty", RedisConstants.MOVIE_INFO_EMPTY_TTL, TimeUnit.MINUTES);
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
        long ttl = RedisConstants.MOVIE_INFO_TTL + RandomUtil.randomInt(10);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(movieVO), ttl, TimeUnit.MINUTES);
        return Result.ok(movieVO);
    }

    @Override
    public Result listMovies(Long current, String title, String genre, String region) {
        //1.查询电影列表
        Page<Movie> page = query()
                .like(StrUtil.isNotBlank(title),"title", title)
                .eq(StrUtil.isNotBlank(genre),"genre", genre)
                .eq(StrUtil.isNotBlank(region),"region", region)
                .eq("status", SystemConstants.MOVIE_STATUS_NORMAL)
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
        Set<String> ids = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 9);
        //2.不存在,不符合正常情况，返回失败
        if(ids == null||ids.isEmpty()){
            return Result.fail("服务器异常");
        }
        //3.根据id查询电影
        List<Long> movieIds = ids.stream()
                .map(Long::valueOf)
                .toList();
        List<Movie> movies = listByIds(movieIds);
        //4.保证Redis中的排序
        Map<Long, Movie> movieMap = movies.stream()
                .collect(Collectors.toMap(
                        Movie::getId,
                        Function.identity()
                ));
        List<MovieSimpleVO> vos = movieIds.stream()
                .map(movieMap::get)
                .filter(Objects::nonNull)
                .map(movie -> BeanUtil.copyProperties(
                        movie,
                        MovieSimpleVO.class
                ))
                .toList();
        return Result.ok(vos);
    }
    @Override
    public void updateHotMovieCache(){
        //1. 查询热门电影
        List<Movie> movies = query()
                .eq("status", SystemConstants.MOVIE_STATUS_NORMAL)
                .orderByDesc("rating_count")
                .orderByDesc("rating_sum")
                .orderByDesc("release_date")
                .last("limit 10")
                .list();
        //2.写入redis
        String oldKey = RedisConstants.HOT_MOVIE_KEY;
        String newKey = oldKey + ":temp";
        stringRedisTemplate.delete(newKey);
        for(Movie movie : movies){
            String value = movie.getId().toString();
            double score = movie.getRatingCount()*10 + movie.getRatingSum().doubleValue();
            stringRedisTemplate.opsForZSet()
                    .add(
                            newKey,
                            value,
                            score
                    );
        }
        stringRedisTemplate.rename(newKey, oldKey);
    }

    @Override
    public Result isFavorite(Long movieId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查redis
        String movieKey = RedisConstants.MOVIE_FAVORITE_KEY + movieId;
        Boolean exists = stringRedisTemplate.hasKey(movieKey);
        if (exists) {
            Boolean isFavorite = stringRedisTemplate.opsForSet()
                    .isMember(movieKey, userId.toString());

            return Result.ok(Boolean.TRUE.equals(isFavorite));
        }
        //3.redis不存在，查数据库重建缓存
        List<Long> ids = movieFavoriteService.query()
                .eq("movie_id", movieId)
                .list()
                .stream()
                .map(MovieFavorite::getUserId)
                .toList();

        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(movieKey, values);
        }

        return Result.ok(ids.contains(userId));
    }

    @Override
    public Result favoriteMovie(Long movieId, Boolean isFavorite) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.判断是收藏还是取消收藏
        if (isFavorite) {
            //3.1.取消收藏
            //删除数据
            boolean isSuccess = movieFavoriteService.remove(new QueryWrapper<MovieFavorite>()
                    .eq("user_id", userId)
                    .eq("movie_id", movieId)
            );
            if(isSuccess){
                log.info("取消收藏成功");
                //移除缓存
                stringRedisTemplate.delete(RedisConstants.MOVIE_FAVORITE_KEY + movieId);
            }
            else{
                log.info("取消收藏失败");
                return Result.fail("取消收藏失败");
            }
        }
        else{
            //3.2.收藏
            //防止收藏不存在的电影
            if(!exists(new QueryWrapper<Movie>().eq("id",movieId))){
                return Result.fail("收藏的电影不存在");
            }
            //防止重复收藏
            boolean exist = movieFavoriteService.query()
                    .eq("user_id", userId)
                    .eq("movie_id", movieId)
                    .exists();
            if(exist){
                return Result.fail("不能重复收藏");
            }
            //新增数据
            MovieFavorite movieFavorite = new MovieFavorite();
            movieFavorite.setUserId(userId);
            movieFavorite.setMovieId(movieId);
            boolean isSuccess = movieFavoriteService.save(movieFavorite);
            if (isSuccess) {
                log.info("收藏成功");
                //移除缓存
                stringRedisTemplate.delete(RedisConstants.MOVIE_FAVORITE_KEY + movieId);
            }
            else{
                log.info("收藏失败");
                return Result.fail("收藏失败");
            }
        }
        return Result.ok();
    }


}