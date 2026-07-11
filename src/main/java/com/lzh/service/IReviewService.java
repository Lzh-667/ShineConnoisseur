package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.dto.ReviewDTO;
import com.lzh.po.Review;

public interface IReviewService extends IService<Review> {
    Result publishReview(ReviewDTO reviewDTO,Long movieId);

    Result listReview(Long movieId, Integer current);

    Result myReviews(Integer current);

    Result likeReview(Long reviewId);

    Result updateReview(Long reviewId, ReviewDTO reviewDTO);

    Result deleteReview(Long reviewId);
}
