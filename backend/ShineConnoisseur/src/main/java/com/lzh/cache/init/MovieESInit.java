package com.lzh.cache.init;

import cn.hutool.core.bean.BeanUtil;
import com.lzh.document.MovieDocument;
import com.lzh.po.Movie;
import com.lzh.repository.MovieSearchRepository;
import com.lzh.service.IMovieService;
import com.lzh.utils.SystemConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@Order(2)
public class MovieESInit implements ApplicationRunner {

    @Resource
    private IMovieService movieService;
    @Resource
    private MovieSearchRepository movieSearchRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Movie> movies = movieService.query()
                    .eq("status", SystemConstants.MOVIE_STATUS_NORMAL)
                    .list();
            if (movies.isEmpty()) {
                log.info("ES 全量同步跳过：无上架电影");
                return;
            }
            List<MovieDocument> docs = movies.stream()
                    .map(m -> BeanUtil.copyProperties(m, MovieDocument.class))
                    .toList();
            movieSearchRepository.saveAll(docs);
            log.info("ES 全量同步完成，共同步 {} 部电影", docs.size());
        } catch (Exception e) {
            log.error("ES 全量同步失败，请检查 ES 是否启动", e);
        }
    }
}
