package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.LikeVO;
import com.lzh.vo.ReviewCommentVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReviewCommentServiceImpl extends ServiceImpl<ReviewCommentMapper, ReviewComment> implements IReviewCommentService {

    @Resource
    private ILikeRecordService likeRecordService;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
        Set<Long> likeReviewCommentIds = getLongs(rcList, userId);
        //5.查询用户
        Map<Long, User> userMap = getUserMap(rcList);
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
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询子评论
        Page<ReviewComment> page = query()
                .eq("root_id",rootId)
                .ne("reply_user_id",0)
                .eq("status",1)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<ReviewComment> rcList = page.getRecords();
        if(rcList.isEmpty()){
            PageResult<ReviewCommentVO> result = new PageResult<>();
            result.setTotal(0L);
            result.setRecords(Collections.emptyList());
            return Result.ok(result);
        }
        Set<Long> likeReviewCommentIds = getLongs(rcList, userId);
        //5.查询用户
        Map<Long, User> userMap = getUserMap(rcList);
        Map<Long, User> replyUserMap = getReplyUserMap(rcList);
        //6.包装为VO
        List<ReviewCommentVO> rcListVO = rcList.stream()
                .map(
                        rc -> {
                            ReviewCommentVO rcVO = new ReviewCommentVO();
                            BeanUtils.copyProperties(rc, rcVO);

                            UserDTO authorDTO = new UserDTO();
                            BeanUtils.copyProperties(userMap.get(rc.getUserId()), authorDTO);
                            rcVO.setAuthor(authorDTO);

                            UserDTO replyUserDTO = new UserDTO();
                            BeanUtils.copyProperties(replyUserMap.get(rc.getReplyUserId()), replyUserDTO);
                            rcVO.setReplyUser(replyUserDTO);

                            rcVO.setCanEditAndDelete(rc.getUserId().equals(userId));

                            rcVO.setIsLike(
                                    likeReviewCommentIds.contains(rc.getId())
                            );
                            return rcVO;
                        }
                ).toList();
        //7.封装并返回
        PageResult<ReviewCommentVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(rcListVO);
        return Result.ok(result);
    }

    @Transactional
    @Override
    public Result likeReviewComment(Long reviewCommentId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.防止点赞不存在的评论
        if(!exists(new QueryWrapper<ReviewComment>().eq("id",reviewCommentId))){
            return Result.fail("点赞的影评不存在");
        }
        //3.判断是否已点赞
        boolean Liked = isLike(reviewCommentId, userId);
        String commentKey = RedisConstants.LIKE_COMMENT_KEY + reviewCommentId;
        if(Liked){
            //3.1.取消点赞
            //删除数据
            boolean isSuccess = likeRecordService.remove(new QueryWrapper<LikeRecord>()
                            .eq("target_id", reviewCommentId)
                            .eq("target_type", SystemConstants.TARGET_COMMENT)
                            .eq("user_id", userId)
            );
            //更新关联数据
            if(isSuccess){
                boolean success = update().setSql("like_count=like_count-1")
                        .eq("id", reviewCommentId)
                        .gt("like_count", 0)
                        .update();
                if(!success){
                    throw new RuntimeException("取消点赞失败");
                }
                log.info("取消点赞成功");
                //移除缓存
                stringRedisTemplate.opsForSet().remove(commentKey, userId.toString());
                Long size = stringRedisTemplate.opsForSet().size(commentKey);
                if(Objects.equals(size, 0L)){
                    stringRedisTemplate.delete(commentKey);
                }
            }
            else{
                log.info("取消点赞失败");
                return Result.fail("取消点赞失败");
            }
            LikeVO likeVO = new LikeVO();
            likeVO.setLike(false);
            likeVO.setLikeCount(getById(reviewCommentId).getLikeCount());
            return Result.ok(likeVO);
        }else{
            //3.2.点赞
            //防止重复点赞
            boolean exist = likeRecordService.query()
                    .eq("user_id", userId)
                    .eq("target_id", reviewCommentId)
                    .eq("target_type", SystemConstants.TARGET_COMMENT)
                    .exists();
            if(exist){
                return Result.fail("不能重复点赞");
            }
            //新增数据
            LikeRecord likeRecord = new LikeRecord();
            likeRecord.setUserId(userId);
            likeRecord.setTargetId(reviewCommentId);
            likeRecord.setTargetType(SystemConstants.TARGET_COMMENT);
            boolean isSuccess = likeRecordService.save(likeRecord);
            //更新关联数据
            if(isSuccess){
                boolean success = update().setSql("like_count=like_count+1")
                        .eq("id", reviewCommentId)
                        .update();
                if(!success){
                    throw new RuntimeException("点赞失败");
                }
                log.info("点赞成功");
                stringRedisTemplate.opsForSet().add(commentKey, userId.toString());
            }
            else{
                log.info("点赞失败");
                return Result.fail("点赞失败");
            }
            LikeVO likeVO = new LikeVO();
            likeVO.setLike(true);
            likeVO.setLikeCount(getById(reviewCommentId).getLikeCount());
            return Result.ok(likeVO);
        }
    }

    @Transactional
    @Override
    public Result deleteReviewComment(Long reviewCommentId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.确认权限
        ReviewComment comment = getById(reviewCommentId);
        if(comment==null){
            return Result.fail("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            return Result.fail("没有删除权限");
        }
        //3.修改数据
        boolean isSuccess = removeById(reviewCommentId);
        if(isSuccess){
            //删除点赞数据和缓存
            likeRecordService.remove(
                    new QueryWrapper<LikeRecord>()
                            .eq("target_id", reviewCommentId)
                            .eq("target_type", SystemConstants.TARGET_COMMENT)
            );
            stringRedisTemplate.delete(RedisConstants.LIKE_COMMENT_KEY + reviewCommentId);
            log.info("删除成功");
            return Result.ok();
        }
        else{
            log.info("删除失败");
            return Result.fail("删除失败");
        }
    }

    @Override
    public Result myReviewComments(Integer current) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.根据用户id查询
        Page<ReviewComment> page = query()
                .eq("user_id",userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<ReviewComment> rcList = page.getRecords();
        if (rcList.isEmpty()) {
            PageResult<ReviewCommentVO> result = new PageResult<>();
            result.setTotal(page.getTotal());
            result.setRecords(Collections.emptyList());
            return Result.ok(result);
        }
        //3.查询当前用户点赞过的评论
        Set<Long> likeReviewCommentIds = getLongs(rcList, userId);
        //4.查询用户信息
        UserDTO authorDTO = new UserDTO();
        BeanUtils.copyProperties(userService.getById(userId), authorDTO);
        Map<Long, User> replyUserMap = getReplyUserMap(rcList);
        //5.将列表转为VO
        List<ReviewCommentVO> rcVOList = rcList.stream()
                .map(comment -> {
                    ReviewCommentVO vo = new ReviewCommentVO();
                    BeanUtils.copyProperties(comment, vo);
                    vo.setAuthor(authorDTO);
                    if(comment.getReplyUserId()!=0){
                        UserDTO replyUserDTO = new UserDTO();
                        BeanUtils.copyProperties(replyUserMap.get(comment.getReplyUserId()), replyUserDTO);
                        vo.setReplyUser(replyUserDTO);
                    }
                    vo.setIsLike(
                            likeReviewCommentIds.contains(comment.getId())
                    );
                    vo.setCanEditAndDelete(true);
                    return vo;
                })
                .toList();
        //6.封装并返回
        PageResult<ReviewCommentVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(rcVOList);
        return Result.ok(result);
    }

    private boolean isLike(Long commentId, Long userId) {
        //2.查redis
        String commentKey = RedisConstants.LIKE_COMMENT_KEY + commentId;
        Boolean exists = stringRedisTemplate.hasKey(commentKey);
        if (exists) {
            Boolean isLike = stringRedisTemplate.opsForSet()
                    .isMember(commentKey, userId.toString());
            return(Boolean.TRUE.equals(isLike));
        }
        //3.redis不存在，查数据库重建缓存
        List<Long> ids = likeRecordService.query()
                .eq("target_id", commentId)
                .eq("target_type", SystemConstants.TARGET_COMMENT)
                .list()
                .stream()
                .map(LikeRecord::getUserId)
                .toList();

        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(commentKey, values);
        }

        return (ids.contains(userId));
    }

    private Map<Long, User> getReplyUserMap(List<ReviewComment> rcList) {
        Set<Long> replyUserIds = rcList.stream()
                .map(ReviewComment::getReplyUserId)
                .filter(id -> id != 0)
                .collect(Collectors.toSet());
        List<User> replyUsers = userService.listByIds(replyUserIds);
        return replyUsers.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity()
                ));
    }

    private Map<Long, User> getUserMap(List<ReviewComment> rcList) {
        Set<Long> userIds = rcList.stream()
                .map(ReviewComment::getUserId)
                .collect(Collectors.toSet());
        List<User> users = userService.listByIds(userIds);
        return users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity()
                ));
    }

    private Set<Long> getLongs(List<ReviewComment> rcList, Long userId) {
        //3.获取评论id
        Set<Long> reviewCommentIds = rcList.stream()
                .map(ReviewComment::getId)
                .collect(Collectors.toSet());
        //4.查询用户点赞过的评论
        return likeRecordService.query()
                .eq("user_id", userId)
                .eq("target_type",SystemConstants.TARGET_COMMENT)
                .in("target_id",reviewCommentIds)
                .list()
                .stream()
                .map(LikeRecord::getTargetId)
                .collect(Collectors.toSet());
    }
}
