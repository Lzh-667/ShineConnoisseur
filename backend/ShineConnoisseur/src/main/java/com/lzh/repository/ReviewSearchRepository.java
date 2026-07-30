package com.lzh.repository;

import com.lzh.document.ReviewDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ReviewSearchRepository extends ElasticsearchRepository<ReviewDocument, Long> {
}
