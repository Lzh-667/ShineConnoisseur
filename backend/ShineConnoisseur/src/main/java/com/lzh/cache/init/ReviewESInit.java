package com.lzh.cache.init;

import cn.hutool.core.bean.BeanUtil;
import com.lzh.document.ReviewDocument;
import com.lzh.po.Movie;
import com.lzh.po.Review;
import com.lzh.repository.ReviewSearchRepository;
import com.lzh.service.IMovieService;
import com.lzh.service.IReviewService;
import com.lzh.utils.SystemConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@Order(3)
public class ReviewESInit implements ApplicationRunner {

    @Resource
    private IReviewService reviewService;
    @Resource
    private IMovieService movieService;
    @Resource
    private ReviewSearchRepository reviewSearchRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Review> reviews = reviewService.query()
                    .eq("status", SystemConstants.REVIEW_STATUS_NORMAL)
                    .list();
            if (reviews.isEmpty()) {
                log.info("ES 影评全量同步跳过：无正常影评");
                return;
            }
            Set<Long> movieIds = reviews.stream().map(Review::getMovieId).collect(Collectors.toSet());
            Map<Long, String> titleMap = movieService.listByIds(movieIds).stream()
                    .collect(Collectors.toMap(Movie::getId, Movie::getTitle));
            List<ReviewDocument> docs = reviews.stream()
                    .map(r -> {
                        ReviewDocument doc = BeanUtil.copyProperties(r, ReviewDocument.class);
                        doc.setMovieTitle(titleMap.getOrDefault(r.getMovieId(), ""));
                        return doc;
                    })
                    .toList();
            reviewSearchRepository.saveAll(docs);
            log.info("ES 影评全量同步完成，共同步 {} 篇影评", docs.size());
        } catch (Exception e) {
            log.error("ES 影评全量同步失败，请检查 ES 是否启动", e);
        }
    }
}
