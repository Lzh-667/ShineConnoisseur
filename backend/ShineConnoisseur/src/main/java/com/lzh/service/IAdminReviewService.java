package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminReviewService {
    Result listReviews(Integer current);

    Result updateReviewStatus(Long id);
}
