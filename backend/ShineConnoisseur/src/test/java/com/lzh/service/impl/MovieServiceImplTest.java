package com.lzh.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.MovieMapper;
import com.lzh.po.Movie;
import com.lzh.po.MovieFavorite;
import com.lzh.service.IMovieFavoriteService;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovieServiceImplTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private IMovieFavoriteService movieFavoriteService;
    @Mock private MovieMapper movieMapper;
    @Mock private QueryChainWrapper<Movie> movieQueryChain;
    @Mock private QueryChainWrapper<MovieFavorite> favoriteQueryChain;
    @Mock private ElasticsearchOperations elasticsearchOperations;

    @Spy @InjectMocks
    private MovieServiceImpl movieService;

    private MockedStatic<UserHolder> userHolderMock;
    private UserDTO mockUser;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        mockUser = new UserDTO();
        mockUser.setId(1L);
        mockUser.setNickname("testUser");
        userHolderMock = mockStatic(UserHolder.class);
        userHolderMock.when(UserHolder::getUser).thenReturn(mockUser);

        // query chain
        doReturn(movieQueryChain).when(movieService).query();
        when(movieQueryChain.eq(any(), any())).thenReturn(movieQueryChain);
        when(movieQueryChain.like(anyBoolean(), any(), any())).thenReturn(movieQueryChain);
        when(movieQueryChain.orderByDesc(anyString())).thenReturn(movieQueryChain);
        when(movieQueryChain.orderByAsc(anyString())).thenReturn(movieQueryChain);
        when(movieQueryChain.last(anyString())).thenReturn(movieQueryChain);
        when(movieQueryChain.list()).thenReturn(Collections.emptyList());

        when(movieFavoriteService.query()).thenReturn(favoriteQueryChain);
        when(favoriteQueryChain.eq(any(), any())).thenReturn(favoriteQueryChain);
        when(favoriteQueryChain.orderByDesc(anyString())).thenReturn(favoriteQueryChain);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    // ==================== getMovieInfo ====================

    @Test
    void getMovieInfo_cacheHit_returnsMovie() {
        Movie movie = buildMovie(1L, "肖申克的救赎", 1);
        String key = RedisConstants.MOVIE_INFO_KEY + 1L;
        when(valueOperations.get(key)).thenReturn(JSONUtil.toJsonStr(movie));

        var result = movieService.getMovieInfo(1L);

        assertTrue(result.getSuccess());
        verify(valueOperations).get(key);
        verify(movieService, never()).getById(anyLong());
    }

    @Test
    void getMovieInfo_cacheEmpty_returnsFail() {
        String key = RedisConstants.MOVIE_INFO_KEY + 1L;
        when(valueOperations.get(key)).thenReturn("empty");

        var result = movieService.getMovieInfo(1L);

        assertFalse(result.getSuccess());
        assertEquals("电影不存在", result.getErrorMsg());
    }

    @Test
    void getMovieInfo_cacheMiss_movieExists_cachesAndReturns() {
        Movie movie = buildMovie(1L, "肖申克的救赎", 1);
        movie.setRatingSum(BigDecimal.valueOf(45));
        movie.setRatingCount(5);
        String key = RedisConstants.MOVIE_INFO_KEY + 1L;
        when(valueOperations.get(key)).thenReturn(null);
        doReturn(movie).when(movieService).getById(1L);

        var result = movieService.getMovieInfo(1L);

        assertTrue(result.getSuccess());
        verify(valueOperations).set(eq(key), anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void getMovieInfo_cacheMiss_movieNotExists_cachesEmpty() {
        String key = RedisConstants.MOVIE_INFO_KEY + 1L;
        when(valueOperations.get(key)).thenReturn(null);
        doReturn(null).when(movieService).getById(1L);

        var result = movieService.getMovieInfo(1L);

        assertFalse(result.getSuccess());
        verify(valueOperations).set(eq(key), eq("empty"), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void getMovieInfo_cacheMiss_movieBanned_returnsFail() {
        Movie movie = buildMovie(1L, "下架电影", 0);
        String key = RedisConstants.MOVIE_INFO_KEY + 1L;
        when(valueOperations.get(key)).thenReturn(null);
        doReturn(movie).when(movieService).getById(1L);

        var result = movieService.getMovieInfo(1L);

        assertFalse(result.getSuccess());
        assertEquals("电影不存在", result.getErrorMsg());
    }

    // ==================== searchMovies ====================

    @Test
    void searchMovies_allFiltersEmpty_returnsEmptyPage() {
        var result = movieService.searchMovies(null, 1, null, null);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
    }

    @Test
    void searchMovies_esFailure_fallsBackToMySQL() {
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(com.lzh.document.MovieDocument.class)))
                .thenThrow(new RuntimeException("ES down"));

        Page<Movie> page = new Page<>(1, 10);
        page.setRecords(List.of(buildMovie(1L, "肖申克的救赎", 1)));
        page.setTotal(1);
        when(movieQueryChain.page(any(Page.class))).thenReturn(page);

        var result = movieService.searchMovies("肖申克", 1, null, null);

        assertTrue(result.getSuccess());
        verify(movieQueryChain).like(true, "title", "肖申克");
    }

    // ==================== listHotMovies ====================

    @Test
    void listHotMovies_normal_returnsMovies() {
        String key = RedisConstants.HOT_MOVIE_KEY;
        when(zSetOperations.size(key)).thenReturn(3L);
        Set<String> idSet = Set.of("1", "2");
        when(zSetOperations.reverseRange(key, 0, 9)).thenReturn(idSet);

        Movie movie1 = buildMovie(1L, "电影1", 1);
        Movie movie2 = buildMovie(2L, "电影2", 1);
        doReturn(List.of(movie1, movie2)).when(movieService).listByIds(anyList());

        var result = movieService.listHotMovies(1);

        assertTrue(result.getSuccess());
    }

    @Test
    void listHotMovies_emptyZSet_returnsEmptyPage() {
        String key = RedisConstants.HOT_MOVIE_KEY;
        when(zSetOperations.size(key)).thenReturn(0L);

        var result = movieService.listHotMovies(1);

        assertTrue(result.getSuccess());
    }

    // ==================== isFavorite ====================

    @Test
    void isFavorite_redisHit_isFavorite_returnsTrue() {
        String key = RedisConstants.USER_FAVORITE_MOVIE_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(true);
        when(zSetOperations.score(key, "10")).thenReturn(1.0);

        var result = movieService.isFavorite(10L);

        assertTrue(result.getSuccess());
        assertTrue((Boolean) result.getData());
    }

    @Test
    void isFavorite_redisHit_notFavorite_returnsFalse() {
        String key = RedisConstants.USER_FAVORITE_MOVIE_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(true);
        when(zSetOperations.score(key, "10")).thenReturn(null);

        var result = movieService.isFavorite(10L);

        assertTrue(result.getSuccess());
        assertFalse((Boolean) result.getData());
    }

    @Test
    void isFavorite_redisMiss_rebuildsFromDB() {
        String key = RedisConstants.USER_FAVORITE_MOVIE_KEY + 1L;
        when(stringRedisTemplate.hasKey(key)).thenReturn(false);

        MovieFavorite mf = new MovieFavorite();
        mf.setUserId(1L);
        mf.setMovieId(10L);
        when(favoriteQueryChain.list()).thenReturn(List.of(mf));

        var result = movieService.isFavorite(10L);

        assertTrue(result.getSuccess());
        assertTrue((Boolean) result.getData());
    }

    // ==================== favoriteMovie ====================

    @Test
    void favoriteMovie_addFavorite_success() {
        doReturn(true).when(movieService).exists(any(QueryWrapper.class));
        when(favoriteQueryChain.exists()).thenReturn(false);
        doReturn(true).when(movieFavoriteService).save(any(MovieFavorite.class));
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        var result = movieService.favoriteMovie(10L, true);

        assertTrue(result.getSuccess());
        verify(movieFavoriteService).save(any(MovieFavorite.class));
    }

    @Test
    void favoriteMovie_addDuplicate_returnsFail() {
        doReturn(true).when(movieService).exists(any(QueryWrapper.class));
        when(favoriteQueryChain.exists()).thenReturn(true);

        var result = movieService.favoriteMovie(10L, true);

        assertFalse(result.getSuccess());
        assertEquals("不能重复收藏", result.getErrorMsg());
    }

    @Test
    void favoriteMovie_addNonExistentMovie_returnsFail() {
        doReturn(false).when(movieService).exists(any(QueryWrapper.class));

        var result = movieService.favoriteMovie(10L, true);

        assertFalse(result.getSuccess());
        assertEquals("收藏的电影不存在", result.getErrorMsg());
    }

    @Test
    void favoriteMovie_removeFavorite_success() {
        doReturn(true).when(movieFavoriteService).remove(any(QueryWrapper.class));
        when(zSetOperations.remove(anyString(), any())).thenReturn(1L);

        var result = movieService.favoriteMovie(10L, false);

        assertTrue(result.getSuccess());
        verify(movieFavoriteService).remove(any(QueryWrapper.class));
    }

    // ==================== listFavoriteMovies ====================

    @Test
    void listFavoriteMovies_redisHit_returnsMovies() {
        String key = RedisConstants.USER_FAVORITE_MOVIE_KEY + 1L;
        when(zSetOperations.size(key)).thenReturn(2L);
        Set<String> idSet = Set.of("10", "20");
        when(zSetOperations.reverseRange(key, 0, 9)).thenReturn(idSet);

        Movie movie1 = buildMovie(10L, "电影10", 1);
        Movie movie2 = buildMovie(20L, "电影20", 1);
        doReturn(List.of(movie1, movie2)).when(movieService).listByIds(anyList());

        var result = movieService.listFavoriteMovies(1);

        assertTrue(result.getSuccess());
    }

    // ==================== helpers ====================

    private Movie buildMovie(Long id, String title, Integer status) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        movie.setStatus(status);
        movie.setRatingSum(BigDecimal.ZERO);
        movie.setRatingCount(0);
        movie.setReleaseDate(LocalDate.of(2024, 1, 1));
        movie.setGenre("剧情");
        movie.setRegion("美国");
        return movie;
    }
}
