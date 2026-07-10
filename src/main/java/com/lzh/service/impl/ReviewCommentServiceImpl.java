package com.lzh.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.dto.ReviewCommentDTO;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.po.LikeRecord;
import com.lzh.po.ReviewComment;
import com.lzh.po.User;
import com.lzh.service.ILikeRecordService;
import com.lzh.service.IReviewCommentService;
import com.lzh.service.IUserService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.ReviewCommentVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewCommentServiceImpl extends ServiceImpl<ReviewCommentMapper, ReviewComment> implements IReviewCommentService {

    @Resource
    private ILikeRecordService likeRecordService;

    @Resource
    private IUserService userService;

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
        //3.如果是回复需要校验rootId
        if(reviewComment.getRootId()!=null && reviewComment.getRootId()!=0){
            ReviewComment rootComment = getById(reviewComment.getRootId());
            if(rootComment == null){
                return Result.fail("评论不存在");
            }
            if(!rootComment.getReviewId().equals(reviewId)){
                return Result.fail("非法评论");
            }
        }
        //4.保存到数据库
        boolean isSuccess = save(reviewComment);
        if(!isSuccess) {
            return Result.fail("添加失败");
        }
        if(reviewCommentDTO.getRootId()==0){
            /*设置一级评论根评论id为自身，回复用户id为0*/
            reviewComment.setRootId(reviewComment.getId());
            reviewComment.setReplyUserId(0L);
            isSuccess = updateById(reviewComment);
            if(!isSuccess){
                return Result.fail("添加失败");
            }
        }
        return Result.ok();
    }

    @Override
    public Result listRootReviewComment(Long reviewId, Integer current) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询一级评论
        Page<ReviewComment> page = query()
                .eq("review_id",reviewId)
                .eq("reply_user_id",0)
                .eq("status",1)
                .orderByDesc("like_count")
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<ReviewComment> rcList = page.getRecords();
        if(rcList.isEmpty()){
            PageResult<ReviewCommentVO> result = new PageResult<>();
            result.setTotal(0L);
            result.setRecords(Collections.emptyList());
            return Result.ok(result);
        }
        //3.获取评论id
        Set<Long> reviewCommentIds = rcList.stream()
                .map(ReviewComment::getId)
                .collect(Collectors.toSet());
        //4.查询用户点赞过的评论
        Set<Long> likeReviewCommentIds = likeRecordService.query()
                .eq("user_id",userId)
                .eq("target_type",SystemConstants.TARGET_COMMENT)
                .in("target_id",reviewCommentIds)
                .list()
                .stream()
                .map(LikeRecord::getTargetId)
                .collect(Collectors.toSet());
        //5.查询用户
        Set<Long> userIds = rcList.stream()
                .map(ReviewComment::getUserId)
                .collect(Collectors.toSet());
        List<User> users = userService.listByIds(userIds);
        Map<Long,User> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity()
                ));
        //6.包装为VO
        List<ReviewCommentVO> rcListVO = rcList.stream()
                .map(
                rc -> {
                    ReviewCommentVO rcVO = new ReviewCommentVO();
                    BeanUtils.copyProperties(rc, rcVO);

                    UserDTO authorDTO = new UserDTO();
                    BeanUtils.copyProperties(userMap.get(rc.getUserId()), authorDTO);
                    rcVO.setAuthor(authorDTO);

                    rcVO.setReplyUser(null);

                    rcVO.setCanEditAndDelete(rc.getUserId().equals(userId));

                    rcVO.setIsLike(
                            likeReviewCommentIds.contains(rc.getId())
                    );

                    return rcVO;
                }).toList();
        //7.封装并返回
        PageResult<ReviewCommentVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(rcListVO);
        return Result.ok(result);
    }

    @Override
    public Result listChildReviewComment(Long rootId, Integer current) {
        return null;
    }
}
