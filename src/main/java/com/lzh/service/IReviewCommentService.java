package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.dto.ReviewCommentDTO;
import com.lzh.po.ReviewComment;

public interface IReviewCommentService extends IService<ReviewComment> {
    Result publishReviewComment(ReviewCommentDTO reviewCommentDTO, Long reviewId);

    Result listRootReviewComment(Long reviewId, Integer current);

    Result listChildReviewComment(Long rootId, Integer current);

    Result likeReviewComment(Long reviewCommentId);

    Result deleteReviewComment(Long reviewCommentId);
}
