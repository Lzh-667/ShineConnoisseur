package com.lzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzh.po.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT * FROM review WHERE status IN (1, 2) ORDER BY create_time DESC")
    IPage<Review> selectAdminPage(Page<Review> page);

    @Select("""
    SELECT *
    FROM review
    WHERE user_id = #{userId}
    AND movie_id = #{movieId}
""")
    Review selectWithDeleted(
            @Param("userId") Long userId,
            @Param("movieId") Long movieId
    );

    @Update("""
    UPDATE review
    SET status = 1,
        title = #{review.title},
        content = #{review.content},
        rating = #{review.rating},
        spoiler = #{review.spoiler},
        update_time = NOW()
    WHERE id = #{review.id}
""")
    boolean restoreReview(@Param("review") Review review);
}
