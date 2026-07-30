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
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.lzh.document.MovieDocument;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Collections;
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
    @Resource
    private ElasticsearchOperations elasticsearchOperations;
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
    public Result listHotMovies(Integer current) {
        String key = RedisConstants.HOT_MOVIE_KEY;
        //1.获取总数
        Long total = stringRedisTemplate.opsForZSet().size(key);
        if (total == null || total == 0) {
            return Result.ok(new PageResult<>(0L, Collections.emptyList()));
        }
        //2.分页查Redis
        int size = SystemConstants.MAX_PAGE_SIZE;
        long start = (long) (current - 1) * size;
        long end = start + size - 1;
        Set<String> ids = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
        if(ids == null || ids.isEmpty()){
            return Result.ok(new PageResult<>(total, Collections.emptyList()));
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
        return Result.ok(new PageResult<>(total, vos));
    }
    @Override
    public void updateHotMovieCache(){
        //1. 查询热门电影
        List<Movie> movies = query()
                .eq("status", SystemConstants.MOVIE_STATUS_NORMAL)
                .orderByDesc("rating_count")
                .orderByDesc("rating_sum")
                .orderByDesc("release_date")
                .last("limit 100")
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
        log.info("更新热门电影缓存成功");
    }
    @Override
    public Result isFavorite(Long movieId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查redis
        String key = RedisConstants.USER_FAVORITE_MOVIE_KEY + userId;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (exists) {
            try {
                Double score = stringRedisTemplate.opsForZSet().score(key, movieId.toString());
                return Result.ok(score != null);
            } catch (Exception e) {
                // redis格式不兼容，删掉走DB
                stringRedisTemplate.delete(key);
            }
        }
        //3.redis不存在，查数据库重建缓存
        List<Long> ids = movieFavoriteService.query()
                .eq("user_id", userId)
                .list()
                .stream()
                .map(MovieFavorite::getMovieId)
                .toList();

        if (!ids.isEmpty()) {
            for(Long id: ids){
                stringRedisTemplate.opsForZSet().add(key,
                        id.toString(),
                        System.currentTimeMillis());
            }
            stringRedisTemplate.expire(key, RedisConstants.USER_FAVORITE_MOVIE_TTL + RandomUtil.randomInt(10), TimeUnit.MINUTES);
        }
        return Result.ok(ids.contains(movieId));
    }
    @Transactional
    @Override
    public Result favoriteMovie(Long movieId, Boolean isFavorite) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.USER_FAVORITE_MOVIE_KEY + userId;
        //2.判断是收藏还是取消收藏
        if (!isFavorite) {
            //3.1.取消收藏
            //删除数据
            boolean isSuccess = movieFavoriteService.remove(new QueryWrapper<MovieFavorite>()
                    .eq("user_id", userId)
                    .eq("movie_id", movieId)
            );
            if(isSuccess){
                log.info("取消收藏成功");
                //移除缓存
                stringRedisTemplate.opsForZSet().remove(key, movieId.toString());
            }
            else{
                log.info("取消收藏失败");
                return Result.fail("取消收藏失败");
            }
        }
        else{
            //3.2.收藏
            //防止收藏不存在的电影
            if(!exists(new QueryWrapper<Movie>().eq("id",movieId).eq("status",SystemConstants.MOVIE_STATUS_NORMAL))){
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
                //增添缓存
                stringRedisTemplate.opsForZSet().add(key,movieId.toString(),System.currentTimeMillis());
                stringRedisTemplate.expire(key,RedisConstants.USER_FAVORITE_MOVIE_TTL + RandomUtil.randomInt(10),TimeUnit.MINUTES);
            }
            else{
                log.info("收藏失败");
                return Result.fail("收藏失败");
            }
        }
        return Result.ok();
    }
    @Override
    public Result listFavoriteMovies(Integer current) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查redis
        String key = RedisConstants.USER_FAVORITE_MOVIE_KEY + userId;
        try {
            Long total = stringRedisTemplate.opsForZSet().size(key);
            if(total!=null && total>0){
                int size = SystemConstants.MAX_PAGE_SIZE;
                long start = (long) (current - 1) * size;
                long end = start + size - 1;
                Set<String> idSet = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
                if (idSet == null || idSet.isEmpty()) {
                    return Result.ok(new PageResult<MovieSimpleVO>(0L, Collections.emptyList()));
                }
                List<Long> ids = idSet.stream()
                        .map(Long::valueOf)
                        .toList();
                Map<Long, Movie> map = listByIds(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                Movie::getId,
                                Function.identity()
                        ));
                List<MovieSimpleVO> vos = ids.stream()
                        .map(map::get)
                        .map(movie -> BeanUtil.copyProperties(movie, MovieSimpleVO.class))
                        .toList();
                return Result.ok(new PageResult<>(total, vos));
            }
        } catch (Exception e) {
            // redis格式不兼容，删掉走DB
            stringRedisTemplate.delete(key);
        }
        //3.redis不存在，查询数据库重建缓存
        Page<MovieFavorite> pageResult = movieFavoriteService.query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        if (pageResult.getTotal() > 0) {
            // 重建全量缓存
            List<MovieFavorite> mfs = movieFavoriteService.query()
                    .eq("user_id", userId)
                    .orderByDesc("create_time")
                    .list();
            for (MovieFavorite mf : mfs) {
                stringRedisTemplate.opsForZSet().add(key,
                        mf.getMovieId().toString(),
                        mf.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            stringRedisTemplate.expire(key, RedisConstants.USER_FAVORITE_MOVIE_TTL + RandomUtil.randomInt(10), TimeUnit.MINUTES);

            List<Long> ids = pageResult.getRecords().stream()
                    .map(MovieFavorite::getMovieId).toList();

            Map<Long, Movie> map = listByIds(ids)
                    .stream()
                    .collect(Collectors.toMap(
                            Movie::getId,
                            Function.identity()
                    ));

            List<MovieSimpleVO> vos = ids.stream()
                    .map(map::get)
                    .map(movie -> BeanUtil.copyProperties(movie, MovieSimpleVO.class))
                    .toList();
            return Result.ok(new PageResult<>(pageResult.getTotal(), vos));
        } else {
            return Result.ok(new PageResult<MovieSimpleVO>(0L, Collections.emptyList()));
        }
    }
    @Override
    public Result searchMovies(String keyword, Integer current, String genre, String region) {
        if (StrUtil.isBlank(keyword) && StrUtil.isBlank(genre) && StrUtil.isBlank(region)) {
            return Result.ok(new PageResult<>(0L, Collections.emptyList()));
        }
        if (current == null || current < 1) {
            current = 1;
        }
        int size = SystemConstants.MAX_PAGE_SIZE;
        // 尝试 ES 搜索
        try {
            NativeQuery query = buildSearchQuery(keyword, genre, region, current, size);
            SearchHits<MovieDocument> hits = elasticsearchOperations.search(query, MovieDocument.class);
            List<Long> ids = hits.getSearchHits().stream()
                    .map(h -> h.getContent().getId())
                    .toList();
            if (ids.isEmpty()) {
                return Result.ok(new PageResult<>(0L, Collections.emptyList()));
            }
            Map<Long, Movie> map = listByIds(ids).stream()
                    .collect(Collectors.toMap(Movie::getId, Function.identity()));
            List<MovieSimpleVO> vos = ids.stream()
                    .map(map::get)
                    .filter(Objects::nonNull)
                    .map(m -> BeanUtil.copyProperties(m, MovieSimpleVO.class))
                    .toList();
            return Result.ok(new PageResult<>(hits.getTotalHits(), vos));
        } catch (Exception e) {
            log.error("ES 搜索失败，降级为 MySQL LIKE 查询: keyword={}", keyword, e);
        }
        // ES 不可用时降级为 MySQL LIKE
        Page<Movie> page = query()
                .like(StrUtil.isNotBlank(keyword), "title", keyword)
                .like(StrUtil.isNotBlank(genre), "genre", genre)
                .like(StrUtil.isNotBlank(region), "region", region)
                .eq("status", SystemConstants.MOVIE_STATUS_NORMAL)
                .orderByDesc("release_date")
                .page(new Page<>(current, size));
        List<MovieSimpleVO> records = page.getRecords().stream()
                .map(m -> BeanUtil.copyProperties(m, MovieSimpleVO.class))
                .toList();
        return Result.ok(new PageResult<>(page.getTotal(), records));
    }
    private NativeQuery buildSearchQuery(String keyword, String genre, String region, int current, int size) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        // 文本搜索
        if (StrUtil.isNotBlank(keyword)) {
            bool.must(Query.of(q -> q.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("title^3", "originalTitle^2", "director^2", "actors")
            )));
        }
        // 类型过滤
        if (StrUtil.isNotBlank(genre)) {
            bool.filter(Query.of(q -> q.wildcard(w -> w.field("genre").value("*" + genre + "*"))));
        }
        // 地区过滤
        if (StrUtil.isNotBlank(region)) {
            bool.filter(Query.of(q -> q.wildcard(w -> w.field("region").value("*" + region + "*"))));
        }
        // 只查上架电影
        bool.filter(Query.of(q -> q.term(t -> t.field("status").value(1))));
        return NativeQuery.builder()
                .withQuery(q -> q.bool(bool.build()))
                .withPageable(PageRequest.of(current - 1, size))
                .build();
    }
}