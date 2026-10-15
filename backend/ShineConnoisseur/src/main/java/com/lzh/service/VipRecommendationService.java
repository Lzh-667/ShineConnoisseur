package com.lzh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.common.Result;
import com.lzh.mapper.MovieMapper;
import com.lzh.mapper.MovieFavoriteMapper;
import com.lzh.mapper.ReviewMapper;
import com.lzh.po.*;
import com.lzh.utils.UserHolder;
import com.lzh.vo.MovieVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** 基于收藏及有效影评评分的内容推荐，不缓存会员授权结果。 */
@Service
@RequiredArgsConstructor
public class VipRecommendationService {
    private final VipIdentityService vipIdentityService;
    private final MovieMapper movieMapper;
    private final MovieFavoriteMapper favoriteMapper;
    private final ReviewMapper reviewMapper;

    public record Item(MovieVO movie, String reason) {}
    public record Recommendations(boolean personalized, List<String> preferredGenres, List<Item> records) {}

    public Result recommend() {
        var user = UserHolder.getUser();
        if (user == null || user.getId() == null || user.getId() <= 0) return Result.fail("请先登录");
        Long userId = user.getId();
        if (!vipIdentityService.isVip(userId)) return Result.fail("个性化推荐为 VIP 专属权益，请开通或续订会员");

        Map<Long, Double> weights = new HashMap<>();
        favoriteMapper.selectList(new LambdaQueryWrapper<MovieFavorite>().eq(MovieFavorite::getUserId, userId))
                .forEach(f -> weights.put(f.getMovieId(), 2.0));
        // 评分覆盖收藏信号，避免把低评分电影误当作爱好。
        reviewMapper.selectList(new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId))
                .forEach(r -> weights.put(r.getMovieId(), r.getRating() == null ? 0.0 : (r.getRating() - 6.0)));
        Set<Long> seen = new HashSet<>(weights.keySet());
        seen.addAll(reviewMapper.selectHistoricalMovieIds(userId));
        Map<String, Double> preferences = new HashMap<>();
        if (!weights.isEmpty()) {
            movieMapper.selectList(new LambdaQueryWrapper<Movie>().in(Movie::getId, weights.keySet()))
                    .forEach(m -> genres(m).forEach(g -> preferences.merge(g, weights.get(m.getId()), Double::sum)));
        }
        List<String> preferred = preferences.keySet().stream().filter(g -> preferences.get(g) > 0)
                .sorted(Comparator.<String>comparingDouble(preferences::get).reversed().thenComparing(g -> g))
                .limit(5).toList();
        boolean hasNegativePreference = preferences.values().stream().anyMatch(weight -> weight < 0);
        boolean personalized = !preferred.isEmpty() || hasNegativePreference;

        // 有界候选集合：偏好类型候选 + 热门候选，避免每次请求扫描并返回整个片库。
        Map<Long, Movie> candidates = new LinkedHashMap<>();
        if (!preferred.isEmpty()) {
            var matching = candidateQuery(seen);
            matching.and(q -> { for (String genre : preferred) q.or().like(Movie::getGenre, genre); });
            movieMapper.selectList(matching).forEach(m -> candidates.put(m.getId(), m));
        }
        movieMapper.selectList(candidateQuery(seen)).forEach(m -> candidates.put(m.getId(), m));
        var ranked = candidates.values().stream();
        // 正向偏好沿用原排序；只有负向信号时稳定降权，同分保留数据库热度顺序。
        if (!preferred.isEmpty()) {
            ranked = ranked.sorted(Comparator.<Movie>comparingDouble(m -> affinity(m, preferences)).reversed()
                        .thenComparing(Comparator.comparingDouble(VipRecommendationService::rating).reversed())
                        .thenComparing(Movie::getId));
        } else if (hasNegativePreference) {
            ranked = ranked.sorted(Comparator.<Movie>comparingDouble(m -> affinity(m, preferences)).reversed());
        }
        List<Item> items = ranked
                .limit(12).map(m -> {
                    MovieVO vo = new MovieVO();
                    BeanUtils.copyProperties(m, vo);
                    if (m.getRatingCount() != null && m.getRatingCount() > 0 && m.getRatingSum() != null)
                        vo.setRating(m.getRatingSum().divide(java.math.BigDecimal.valueOf(m.getRatingCount()), 1, RoundingMode.HALF_UP));
                    String matches = genres(m).stream().filter(preferred::contains).sorted().collect(Collectors.joining("、"));
                    String reason = matches.isEmpty() ? "站内热门候选，探索新的观影类型" : "结合你的收藏与评分，为你推荐" + matches + "电影";
                    if (preferred.isEmpty() && hasNegativePreference) {
                        reason = affinity(m, preferences) < 0
                                ? "包含你曾低评分的类型，已降低推荐优先级，仅供探索"
                                : "结合你的评分，优先探索未表现出负向偏好的类型";
                    }
                    return new Item(vo, reason);
                }).toList();
        return Result.ok(new Recommendations(personalized, preferred, items));
    }

    private LambdaQueryWrapper<Movie> candidateQuery(Set<Long> seen) {
        return new LambdaQueryWrapper<Movie>().eq(Movie::getStatus, 1)
                .notIn(!seen.isEmpty(), Movie::getId, seen)
                .orderByDesc(Movie::getRatingCount).orderByDesc(Movie::getId).last("LIMIT 300");
    }

    static Set<String> genres(Movie movie) {
        if (movie.getGenre() == null || movie.getGenre().isBlank()) return Set.of();
        return Arrays.stream(movie.getGenre().split("[,，/、|]"))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    static double affinity(Movie movie, Map<String, Double> preferences) {
        return genres(movie).stream().mapToDouble(g -> preferences.getOrDefault(g, 0.0)).sum();
    }

    private static double rating(Movie movie) {
        return movie.getRatingCount() == null || movie.getRatingCount() <= 0 || movie.getRatingSum() == null
                ? 0 : movie.getRatingSum().doubleValue() / movie.getRatingCount();
    }
}
