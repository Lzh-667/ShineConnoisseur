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
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewCommentServiceImpl extends ServiceImpl<ReviewCommentMapper, ReviewComment> implements IReviewCommentService {

    @Transactional
    @Override
    public Result publishReviewComment(ReviewCommentDTO reviewCommentDTO, Long reviewId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.将DTO转化为ReviewComment
        ReviewComment reviewComment = new ReviewComment();
        BeanUtils.copyProperties(reviewCommentDTO,reviewComment);
        reviewComment.setUserId(userId);
        reviewComment.setReviewId(reviewId);
        //3.保存到数据库
        boolean isSuccess1 = save(reviewComment);
        if(!isSuccess1) {
            return Result.fail("添加失败");
        }
        if(reviewCommentDTO.getRootId()==0){
            reviewComment.setRootId(reviewComment.getId());
            boolean isSuccess2 = updateById(reviewComment);
            if(!isSuccess2){
                return Result.fail("添加失败");
            }
        }
        return Result.ok();
    }
}
