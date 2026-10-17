package com.lzh.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.*;
import com.lzh.po.*;
import com.lzh.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class VipRecommendationServiceTest {
    private final VipIdentityService identity = mock(VipIdentityService.class);
    private final MovieMapper movies = mock(MovieMapper.class);
    private final MovieFavoriteMapper favorites = mock(MovieFavoriteMapper.class);
    private final ReviewMapper reviews = mock(ReviewMapper.class);
    private final VipRecommendationService service = new com.lzh.service.impl.VipRecommendationServiceImpl(identity, movies, favorites, reviews);

    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "recommendation-test");
        for (Class<?> type : List.of(Movie.class, MovieFavorite.class, Review.class))
            TableInfoHelper.initTableInfo(assistant, type);
    }

    @BeforeEach
    void user() {
        UserDTO user = new UserDTO(); user.setId(1L); UserHolder.setUser(user);
    }

    @AfterEach
    void cleanup() { UserHolder.removeUser(); }

    @Test
    void nonVipCannotReadRecommendations() {
        assertFalse(service.recommend().getSuccess());
        verifyNoInteractions(movies, favorites, reviews);
    }

    @Test
    void guestCannotReadRecommendations() {
        UserHolder.removeUser();
        assertFalse(service.recommend().getSuccess());
        verifyNoInteractions(identity, movies, favorites, reviews);
    }

    @Test
    void newVipGetsFallbackAndMembershipIsRechecked() {
        when(identity.isVip(1L)).thenReturn(true, false);
        when(favorites.selectList(any())).thenReturn(List.of());
        when(reviews.selectList(any())).thenReturn(List.of());
        when(movies.selectList(any())).thenReturn(List.of(movie(3L, "科幻")));
        var result = (VipRecommendationService.Recommendations) service.recommend().getData();
        assertFalse(result.personalized());
        assertEquals(1, result.records().size());
        assertNull(result.records().getFirst().movie().getRating());
        assertFalse(service.recommend().getSuccess());
    }

    @Test
    void lowRatingOverridesFavoriteAndPositiveGenreRanksFirst() {
        when(identity.isVip(1L)).thenReturn(true);
        MovieFavorite favorite = new MovieFavorite(); favorite.setMovieId(10L);
        Review disliked = new Review(); disliked.setMovieId(10L); disliked.setRating(2);
        Review liked = new Review(); liked.setMovieId(11L); liked.setRating(9);
        when(favorites.selectList(any())).thenReturn(List.of(favorite));
        when(reviews.selectList(any())).thenReturn(List.of(disliked, liked));
        when(movies.selectList(any())).thenReturn(List.of(movie(10L, "恐怖"), movie(11L, "科幻")),
                List.of(movie(20L, "科幻")), List.of(movie(21L, "恐怖"), movie(20L, "科幻")));
        var result = (VipRecommendationService.Recommendations) service.recommend().getData();
        assertTrue(result.personalized());
        assertEquals(List.of("科幻"), result.preferredGenres());
        assertEquals(20L, result.records().getFirst().movie().getId());
        assertEquals(2, result.records().size());
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(movies, times(3)).selectList(captor.capture());
        var candidate = captor.getAllValues().get(1);
        assertTrue(candidate.getSqlSegment().contains("NOT IN"));
        assertTrue(candidate.getParamNameValuePairs().containsValue(10L));
        assertTrue(candidate.getParamNameValuePairs().containsValue(11L));
        assertTrue(candidate.getSqlSegment().contains("status"));
    }

    private static Movie movie(Long id, String genre) {
        Movie movie = new Movie(); movie.setId(id); movie.setGenre(genre); movie.setStatus(1);
        return movie;
    }

    @Test
    void negativeOnlyPreferencesDemoteDislikedGenresAndKeepPopularityWithinTies() {
        when(identity.isVip(1L)).thenReturn(true);
        Review disliked = new Review(); disliked.setMovieId(10L); disliked.setRating(2);
        when(favorites.selectList(any())).thenReturn(List.of());
        when(reviews.selectList(any())).thenReturn(List.of(disliked));
        // 数据库热度顺序：恐怖片、普通剧情片、单次满分科幻片、另一部恐怖片。
        Movie neutralPopular = movie(22L, "剧情");
        neutralPopular.setRatingCount(100); neutralPopular.setRatingSum(new java.math.BigDecimal("800"));
        Movie neutralPerfect = movie(21L, "科幻");
        neutralPerfect.setRatingCount(1); neutralPerfect.setRatingSum(new java.math.BigDecimal("10"));
        when(movies.selectList(any())).thenReturn(List.of(movie(10L, "恐怖")),
                List.of(movie(30L, "恐怖"), neutralPopular, neutralPerfect, movie(29L, "恐怖")));

        var result = (VipRecommendationService.Recommendations) service.recommend().getData();

        assertTrue(result.personalized());
        assertTrue(result.preferredGenres().isEmpty());
        assertEquals(List.of(22L, 21L, 30L, 29L), result.records().stream().map(i -> i.movie().getId()).toList());
        assertTrue(result.records().getFirst().reason().contains("未表现出负向偏好"));
        assertTrue(result.records().get(2).reason().contains("降低推荐优先级"));
    }

    @Test
    void neutralAndCancelledSignalsPreservePopularity() {
        when(identity.isVip(1L)).thenReturn(true);
        Review positive = new Review(); positive.setMovieId(10L); positive.setRating(8);
        Review negative = new Review(); negative.setMovieId(11L); negative.setRating(4);
        Review neutral = new Review(); neutral.setMovieId(12L); neutral.setRating(6);
        when(favorites.selectList(any())).thenReturn(List.of());
        when(reviews.selectList(any())).thenReturn(List.of(positive, negative, neutral));
        when(movies.selectList(any())).thenReturn(List.of(movie(10L, "科幻"), movie(11L, "科幻"), movie(12L, "剧情")),
                List.of(movie(30L, "科幻"), movie(20L, "剧情")));

        var result = (VipRecommendationService.Recommendations) service.recommend().getData();

        assertFalse(result.personalized());
        assertTrue(result.preferredGenres().isEmpty());
        assertEquals(List.of(30L, 20L), result.records().stream().map(i -> i.movie().getId()).toList());
    }

    @Test
    void negativeOnlyCandidatesAreDemotedRatherThanRemoved() {
        when(identity.isVip(1L)).thenReturn(true);
        Review disliked = new Review(); disliked.setMovieId(10L); disliked.setRating(2);
        when(favorites.selectList(any())).thenReturn(List.of());
        when(reviews.selectList(any())).thenReturn(List.of(disliked));
        when(movies.selectList(any())).thenReturn(List.of(movie(10L, "恐怖,惊悚")),
                List.of(movie(30L, "恐怖,惊悚"), movie(20L, "恐怖")));

        var result = (VipRecommendationService.Recommendations) service.recommend().getData();

        assertTrue(result.personalized());
        assertEquals(List.of(20L, 30L), result.records().stream().map(i -> i.movie().getId()).toList());
    }

    @Test
    void historicalReviewsAreExcludedWithoutContributingPreferences() {
        when(identity.isVip(1L)).thenReturn(true);
        when(favorites.selectList(any())).thenReturn(List.of());
        when(reviews.selectList(any())).thenReturn(List.of());
        // 正常影评查询为空，但删除或封禁的影评仍有历史电影 ID。
        when(reviews.selectHistoricalMovieIds(1L)).thenReturn(List.of(10L, 11L));
        when(movies.selectList(any())).thenReturn(List.of(movie(20L, "科幻")));

        var result = (VipRecommendationService.Recommendations) service.recommend().getData();

        assertFalse(result.personalized());
        assertTrue(result.preferredGenres().isEmpty());
        verify(reviews).selectHistoricalMovieIds(1L);
        var captor = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        // 历史电影没有进入画像查询，仅用于候选排除。
        verify(movies).selectList(captor.capture());
        var query = captor.getValue();
        assertTrue(query.getSqlSegment().contains("NOT IN"));
        assertTrue(query.getParamNameValuePairs().containsValue(10L));
        assertTrue(query.getParamNameValuePairs().containsValue(11L));
    }

    @Test
    void fallbackPreservesPopularityInsteadOfPromotingSinglePerfectRating() {
        when(identity.isVip(1L)).thenReturn(true);
        when(favorites.selectList(any())).thenReturn(List.of());
        when(reviews.selectList(any())).thenReturn(List.of());
        Movie popular = movie(20L, "剧情");
        popular.setRatingCount(1000);
        popular.setRatingSum(new java.math.BigDecimal("8000"));
        Movie singleRating = movie(21L, "科幻");
        singleRating.setRatingCount(1);
        singleRating.setRatingSum(new java.math.BigDecimal("10"));
        when(movies.selectList(any())).thenReturn(List.of(popular, singleRating));

        var result = (VipRecommendationService.Recommendations) service.recommend().getData();

        assertFalse(result.personalized());
        assertEquals(List.of(20L, 21L), result.records().stream().map(i -> i.movie().getId()).toList());
        assertEquals(new java.math.BigDecimal("8.0"), result.records().getFirst().movie().getRating());
    }
}
