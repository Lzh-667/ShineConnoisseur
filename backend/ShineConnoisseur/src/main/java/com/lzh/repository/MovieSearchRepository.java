package com.lzh.repository;

import com.lzh.document.MovieDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MovieSearchRepository extends ElasticsearchRepository<MovieDocument, Long> {
}
