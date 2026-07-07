package com.lzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzh.po.ReviewComment;
import io.lettuce.core.dynamic.annotation.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface ReviewCommentMapper extends BaseMapper<ReviewComment> {

    List<Map<String, Object>> countByReviewIds(@Param("reviewIds") Set<Long> reviewIds);
}
