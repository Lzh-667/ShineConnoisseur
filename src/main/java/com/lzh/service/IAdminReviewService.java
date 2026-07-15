package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminReviewService {
    Result listReviews(Long current);

    Result updateReviewStatus(Long id);
}
