package com.lzh.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.Result;
import com.lzh.dto.ReviewCommentDTO;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.po.ReviewComment;
import com.lzh.service.IReviewCommentService;
import com.lzh.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
public class ReviewCommentServiceImpl extends ServiceImpl<ReviewCommentMapper, ReviewComment> implements IReviewCommentService {

    @Override
    public Result publishReviewComment(ReviewCommentDTO reviewCommentDTO, Long reviewId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.将DTO转化为ReviewComment
        ReviewComment reviewComment = new ReviewComment();
        BeanUtils.copyProperties(reviewCommentDTO,reviewComment);
        reviewComment.setUserId(userId);
        reviewComment.setReviewId(reviewId);
        if(reviewComment.getParentId() != 0){
            ReviewComment parentComment = query().eq("id", reviewComment.getParentId()).one();
            if(parentComment == null){
                return Result.fail("回复的评论不存在");
            }
            if(!parentComment.getReviewId().equals(reviewId)){
                return Result.fail("非法评论");
            }
            reviewComment.setReplyUserId(parentComment.getUserId());
        }
        //3.保存到数据库
        boolean isSuccess = save(reviewComment);
        if(!isSuccess) {
            return Result.fail("添加失败");
        }
        return Result.ok();
    }
}
