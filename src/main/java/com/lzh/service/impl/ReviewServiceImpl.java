package com.lzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.Result;
import com.lzh.dto.ReviewDTO;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.mapper.ReviewMapper;
import com.lzh.po.*;
import com.lzh.service.*;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.ReviewVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    @Resource
    private IMovieService movieService;
    @Resource
    private IUserService userService;
    @Resource
    private ILikeRecordService likeRecordService;
    @Resource
    private ReviewCommentMapper reviewCommentMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    @Override
    public Result publishReview(ReviewDTO reviewDTO,Long movieId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.将DTO转为Review
        Review review = new Review();
        BeanUtils.copyProperties(reviewDTO, review);
        review.setUserId(userId);
        review.setMovieId(movieId);
        //3.保存影评到数据库
        boolean isSuccess = save(review);
        if(!isSuccess) {
            return Result.fail("添加失败");
        }
        //4.更改各表数据
        //4.1.更改movie数据
        movieService.update()
                .setSql("rating_sum = rating_sum + " + review.getRating() +
                        ", rating_count = rating_count + 1")
                .eq("id",review.getMovieId())
                .update();
        //4.2.更改user数据
        userService.update()
                .setSql("review_count=review_count+1")
                .eq("id", userId)
                .update();

        return Result.ok();
    }

    @Override
    public Result listReview(Long movieId, Integer current) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.根据movieId查询movie
        Movie movie = movieService.getById(movieId);
        //3.判断movie是否存在
        if (movie == null) {
            return Result.fail("电影不存在");
        }
        //4.查询影评列表
        Page<Review> page = query()
                .eq("movie_id", movieId)
                .orderByDesc("like_count")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Review> reviewList = page.getRecords();
        if (reviewList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        //5.获取影评id和用户id
        Set<Long> reviewIds = reviewList.stream()
                .map(Review::getId)
                .collect(Collectors.toSet());

        Set<Long> userIds = reviewList.stream()
                .map(Review::getUserId)
                .collect(Collectors.toSet());

        //6.批量查询用户
        Map<Long, User> userMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user
                ));

        //7.查询当前用户点赞过的影评
        Set<Long> likeReviewIds = getLikeReviewIds(userId, reviewIds);

        //8.批量查询评论数量
        Map<Long, Integer> commentCountMap = getCommentCountMap(reviewIds);

        //9.将列表转换为VO
        List<ReviewVO> reviewVOList = reviewList.stream()
                .map(review -> {
                    ReviewVO vo = new ReviewVO();
                    BeanUtils.copyProperties(review, vo);
                    User user = userMap.get(review.getUserId());
                    if (user != null) {
                        vo.setUserName(user.getUsername());
                        vo.setNickName(user.getNickname());
                        vo.setAvatar(user.getAvatar());
                    }
                    vo.setIsLike(
                            likeReviewIds.contains(review.getId())
                    );
                    vo.setCommentCount(
                            commentCountMap.getOrDefault(review.getId(), 0)
                    );
                    vo.setCanEditAndDelete(
                            review.getUserId().equals(userId)
                    );
                    return vo;
                })
                .toList();
        return Result.ok(reviewVOList);
    }

    @Override
    public Result myReviews(Integer current) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.根据用户id查询
        Page<Review> page = query()
                .eq("user_id",userId)
                .orderByDesc("like_count")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Review> reviewList = page.getRecords();
        if (reviewList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        //3.获取影评id
        Set<Long> reviewIds = reviewList.stream()
                .map(Review::getId)
                .collect(Collectors.toSet());

        //4.查询当前用户点赞过的影评
        Set<Long> likeReviewIds = getLikeReviewIds(userId, reviewIds);

        //5.批量查询评论数量
        Map<Long, Integer> commentCountMap = getCommentCountMap(reviewIds);

        //6.将列表转为VO
        User user=userService.getById(userId);
        String userName = user.getUsername();
        String nickname = user.getNickname();
        String avatar = user.getAvatar();
        List<ReviewVO> reviewVOList = reviewList.stream()
                .map(review -> {
                    ReviewVO vo = new ReviewVO();
                    BeanUtils.copyProperties(review, vo);
                    vo.setUserName(userName);
                    vo.setNickName(nickname);
                    vo.setAvatar(avatar);
                    vo.setIsLike(
                            likeReviewIds.contains(review.getId())
                    );
                    vo.setCommentCount(
                            commentCountMap.getOrDefault(review.getId(), 0)
                    );
                    vo.setCanEditAndDelete(true);
                    return vo;
                })
                .toList();
        return Result.ok(reviewVOList);
    }

    @Transactional
    @Override
    public Result likeReview(Long reviewId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.防止点赞不存在的影评
        if(!exists(new QueryWrapper<Review>().eq("id",reviewId))){
            return Result.fail("点赞的影评不存在");
        }
        //3.判断是点赞还是取消点赞
        boolean Liked = isLike(reviewId, userId);
        if (Liked) {
            //3.1.取消点赞
            //删除数据
            boolean isSuccess = likeRecordService.remove(new QueryWrapper<LikeRecord>()
                    .eq("user_id", userId)
                    .eq("target_id", reviewId)
                    .eq("target_type", SystemConstants.TARGET_REVIEW));
            if(isSuccess){
                //更新点赞数量
                boolean success=update().setSql("like_count=like_count-1")
                        .eq("id", reviewId)
                        .gt("like_count", 0)
                        .update();
                if(!success){
                    throw new RuntimeException("更新点赞数量失败");
                }
                log.info("取消点赞成功");
                //移除缓存
                stringRedisTemplate.delete(RedisConstants.LIKE_REVIEW_KEY + reviewId);
            }
            else{
                log.info("取消点赞失败");
                return Result.fail("取消点赞失败");
            }
        }
        else{
            //3.2.点赞
            //防止重复点赞
            boolean exist = likeRecordService.query()
                    .eq("user_id", userId)
                    .eq("target_id", reviewId)
                    .eq("target_type", SystemConstants.TARGET_REVIEW)
                    .exists();
            if(exist){
                return Result.fail("不能重复点赞");
            }
            //新增数据
            LikeRecord likeRecord = new LikeRecord();
            likeRecord.setUserId(userId);
            likeRecord.setTargetId(reviewId);
            likeRecord.setTargetType(SystemConstants.TARGET_REVIEW);
            boolean isSuccess = likeRecordService.save(likeRecord);
            if (isSuccess) {
                //4.更新点赞数量
                boolean success=update().setSql("like_count=like_count+1")
                        .eq("id", reviewId)
                        .update();
                if(!success){
                    throw new RuntimeException("更新点赞数量失败");
                }
                log.info("点赞成功");
                //移除缓存
                stringRedisTemplate.delete(RedisConstants.LIKE_REVIEW_KEY + reviewId);
            }
            else{
                log.info("点赞失败");
                return Result.fail("点赞失败");
            }
        }
        return Result.ok();
    }

    public boolean isLike(Long reviewId,Long userId) {
        //2.查redis
        String reviewKey = RedisConstants.LIKE_REVIEW_KEY + reviewId;
        Boolean exists = stringRedisTemplate.hasKey(reviewKey);
        if (exists) {
            Boolean isLike = stringRedisTemplate.opsForSet()
                    .isMember(reviewKey, userId.toString());

            return Boolean.TRUE.equals(isLike);
        }
        //3.redis不存在，查数据库重建缓存
        List<Long> ids = likeRecordService.query()
                .eq("target_id", reviewId)
                .eq("target_type", SystemConstants.TARGET_REVIEW)
                .list()
                .stream()
                .map(LikeRecord::getUserId)
                .toList();

        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(reviewKey, values);
        }

        return (ids.contains(userId));
    }

    @Transactional
    @Override
    public Result updateReview(Long reviewId, ReviewDTO reviewDTO) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.确认权限
        Review review = getById(reviewId);
        if(review==null){
            return Result.fail("影评不存在");
        }
        Integer oldRating = review.getRating();
        Long movieId = review.getMovieId();
        if (!review.getUserId().equals(userId)) {
            return Result.fail("没有修改权限");
        }
        //3.修改数据
        boolean isSuccess =update().set("rating", reviewDTO.getRating())
                .set("title", reviewDTO.getTitle())
                .set("content", reviewDTO.getContent())
                .set("spoiler", reviewDTO.getSpoiler())
                .eq("id", reviewId)
                .update();
        if(isSuccess){
            //修改电影总评分
            int diff = reviewDTO.getRating() - oldRating;
            boolean success=movieService.update()
                    .setSql("rating_sum=rating_sum+" + diff)
                    .eq("id", movieId)
                    .update();
            if(!success){
                throw new RuntimeException("更新电影总评分失败");
            }
            log.info("修改成功");
            return Result.ok();
        }
        else{
            log.info("修改失败");
            return Result.fail("修改失败");
        }
    }

    @Transactional
    @Override
    public Result deleteReview(Long reviewId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.确认权限
        Review review = getById(reviewId);
        if(review==null){
            return Result.fail("影评不存在");
        }
        Integer oldRating = review.getRating();
        Long movieId = review.getMovieId();
        if (!review.getUserId().equals(userId)) {
            return Result.fail("没有删除权限");
        }
        //3.修改数据
        boolean isSuccess = removeById(reviewId);
        if(isSuccess){
            //修改电影数据
            boolean success1=movieService.update()
                    .setSql("rating_sum=rating_sum-" + oldRating)
                    .setSql("rating_count = rating_count-1")
                    .gt("rating_count", 0)
                    .eq("id", movieId)
                    .update();
            //修改个人数据
            boolean success2=userService.update()
                    .setSql("review_count=review_count-1")
                    .gt("review_count", 0)
                    .eq("id", userId)
                    .update();
            if(!success1||!success2){
                throw new RuntimeException("更新关联数据失败");
            }
            //删除点赞数据和缓存
            likeRecordService.remove(
                    new QueryWrapper<LikeRecord>()
                            .eq("target_id", reviewId)
                            .eq("target_type", SystemConstants.TARGET_REVIEW)
            );
            stringRedisTemplate.delete(RedisConstants.LIKE_REVIEW_KEY + reviewId);
            log.info("删除成功");
            return Result.ok();
        }
        else{
            log.info("删除失败");
            return Result.fail("删除失败");
        }
    }

    private Set<Long> getLikeReviewIds(Long userId, Set<Long> reviewIds) {
        return likeRecordService.query()
                .eq("user_id", userId)
                .eq("target_type", SystemConstants.TARGET_REVIEW)
                .in("target_id", reviewIds)
                .list()
                .stream()
                .map(LikeRecord::getTargetId)
                .collect(Collectors.toSet());
    }

    private Map<Long, Integer> getCommentCountMap(Set<Long> reviewIds) {
        return reviewCommentMapper.countByReviewIds(reviewIds)
                        .stream()
                        .collect(Collectors.toMap(
                                map -> ((Number) map.get("review_id")).longValue(),
                                map -> ((Number) map.get("count")).intValue()
                        ));
    }
}
