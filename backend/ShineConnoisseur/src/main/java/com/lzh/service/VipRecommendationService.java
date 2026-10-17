package com.lzh.service;

import com.lzh.common.Result;
import com.lzh.vo.MovieVO;
import java.util.List;

/** VIP 个性化推荐契约。 */
public interface VipRecommendationService {
    public record Item(MovieVO movie, String reason) {}

    public record Recommendations(boolean personalized, List<String> preferredGenres, List<Item> records) {}

    Result recommend();
}
