package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.BusinessException;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.common.ScrollResult;
import com.lzh.document.ReviewDocument;
import com.lzh.dto.MessageDTO;
import com.lzh.dto.ReviewDTO;
import com.lzh.dto.ReviewHotDTO;
import com.lzh.mapper.ReviewCommentMapper;
import com.lzh.mapper.ReviewMapper;
import com.lzh.po.*;
import com.lzh.repository.ReviewSearchRepository;
import com.lzh.service.*;
import com.lzh.utils.MQConstants;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.LikeVO;
import com.lzh.vo.ReviewVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private ReviewCommentMapper reviewCommentMapper;
    @Resource
    private ReviewMapper reviewMapper;
    @Resource
    private ElasticsearchOperations elasticsearchOperations;
    @Resource
    private ReviewSearchRepository reviewSearchRepository;
    @Transactional
    @Override
    public Result publishReview(ReviewDTO reviewDTO,Long movieId) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //检查
        Movie movie = movieService.getById(movieId);
        if (movie == null|| !Objects.equals(movie.getStatus(), SystemConstants.MOVIE_STATUS_NORMAL)) {
            return Result.fail("电影不存在");
        }
        Result fail = check(reviewDTO);
        if (fail != null) return fail;
        Review exist = reviewMapper.selectWithDeleted(userId, movieId);
        if (exist!=null){
            // 已经有正常影评
            if(Objects.equals(exist.getStatus(), SystemConstants.REVIEW_STATUS_NORMAL)){
                return Result.fail("您已对该电影发表过影评，请勿重复提交");
            }
            // 用户删除过，恢复
            if(Objects.equals(exist.getStatus(), SystemConstants.REVIEW_STATUS_DELETE)){
                exist.setStatus(SystemConstants.REVIEW_STATUS_NORMAL);
                exist.setTitle(reviewDTO.getTitle());
                exist.setContent(reviewDTO.getContent());
                exist.setRating(reviewDTO.getRating());
                exist.setSpoiler(reviewDTO.getSpoiler());
                boolean success = reviewMapper.restoreReview(exist);
                if(!success){
                    return Result.fail("恢复影评失败");
                }
                // 恢复评分统计
                movieService.update()
                        .setSql("rating_sum = rating_sum + " + exist.getRating()
                                + ", rating_count = rating_count + 1")
                        .eq("id", movieId)
                        .update();
                // 恢复用户影评数量
                userService.update()
                        .setSql("review_count = review_count + 1")
                        .eq("id", userId)
                        .update();
                stringRedisTemplate.delete(
                        RedisConstants.MOVIE_INFO_KEY + movieId
                );
                syncReviewToES(exist);
                return Result.ok();
            }
            // 管理员封禁的情况
            if(Objects.equals(exist.getStatus(), SystemConstants.REVIEW_STATUS_BAN)){
                return Result.fail("该影评被管理员处理，无法重新发布");
            }
        }
        // 2. 将DTO转为Review
        Review review = new Review();
        BeanUtils.copyProperties(reviewDTO, review);
        review.setUserId(userId);
        review.setMovieId(movieId);
        // 3. 保存影评到数据库
        boolean isSuccess = save(review);
        if(!isSuccess) {
            return Result.fail("添加失败");
        }
        // 4. 更改各表数据
        // 4.1. 更改movie数据
        movieService.update()
                .setSql("rating_sum = rating_sum + " + review.getRating() +
                        ", rating_count = rating_count + 1")
                .eq("id",review.getMovieId())
                .update();
        // 4.2. 更改user数据
        userService.update()
                .setSql("review_count=review_count+1")
                .eq("id", userId)
                .update();
        // 5.删除缓存
        stringRedisTemplate.delete(RedisConstants.MOVIE_INFO_KEY + movieId);
        // 6.同步ES
        syncReviewToES(review);
        return Result.ok();
    }

    @Override
    public Result listReview(Long movieId, Integer current) {
        if(current== null|| current < 1){
            current = 1;
        }
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 根据movieId查询movie
        Movie movie = movieService.getById(movieId);
        // 3. 判断movie是否存在
        if (movie == null|| !Objects.equals(movie.getStatus(), SystemConstants.MOVIE_STATUS_NORMAL)) {
            return Result.fail("电影不存在");
        }
        // 4. 查询影评列表
        Page<Review> page = query()
                .eq("movie_id", movieId)
                .eq("status", SystemConstants.REVIEW_STATUS_NORMAL)
                .orderByDesc("like_count")
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Review> reviewList = page.getRecords();
        if (reviewList.isEmpty()) {
            return Result.ok(new PageResult<>(0L, Collections.emptyList()));
        }
        List<ReviewVO> vos = buildReviewVOS(reviewList, userId);
        return Result.ok(new PageResult<>(page.getTotal(), vos));
    }
    @Override
    public Result getReviewDetail(Long reviewId) {
        Long userId = UserHolder.getUser().getId();
        Review review = query()
                .eq("id", reviewId)
                .eq("status", SystemConstants.REVIEW_STATUS_NORMAL)
                .one();
        if (review == null) {
            return Result.fail("影评不存在");
        }
        User author = userService.getById(review.getUserId());
        Set<Long> likeReviewIds = getLikeReviewIds(userId, Set.of(reviewId));
        List<ReviewVO> vos = getReviewVOList(
                List.of(review),
                author != null ? Map.of(author.getId(), author) : Collections.emptyMap(),
                likeReviewIds,
                userId,
                getMovieMap(List.of(review))
        );
        return Result.ok(vos.getFirst());
    }
    @Override
    public Result myReviews(Integer current) {
        return buildUserReviews(UserHolder.getUser().getId(), current);
    }
    @Override
    public Result getUserReviews(Long userId, Integer current) {
        User user = userService.getById(userId);
        if (user == null || !Objects.equals(user.getStatus(), SystemConstants.USER_STATUS_NORMAL)) {
            return Result.fail("用户不存在");
        }
        return buildUserReviews(userId, current);
    }

    private Result buildUserReviews(Long targetUserId, Integer current) {
        if (current == null || current < 1) {
            current = 1;
        }
        Long currentUserId = UserHolder.getUser().getId();
        int size = SystemConstants.MAX_PAGE_SIZE;
        Page<Review> page = query()
                .eq("user_id", targetUserId)
                .eq("status", SystemConstants.REVIEW_STATUS_NORMAL)
                .orderByDesc("like_count")
                .page(new Page<>(current, size + 1));

        List<Review> reviewList = page.getRecords();
        if (reviewList.isEmpty()) {
            return Result.ok(new ScrollResult<>(Collections.emptyList(), false));
        }
        boolean hasMore = reviewList.size() > size;
        if (hasMore) {
            reviewList = reviewList.subList(0, size);
        }

        Set<Long> reviewIds = getReviewIds(reviewList);
        Set<Long> userIds = getUserIds(reviewList);
        Set<Long> likeReviewIds = getLikeReviewIds(currentUserId, reviewIds);
        Map<Long, User> userMap = getUserMap(userIds);
        Map<Long, Movie> movieMap = getMovieMap(reviewList);

        List<ReviewVO> reviewVOList = getReviewVOList(reviewList, userMap, likeReviewIds, currentUserId, movieMap);
        return Result.ok(new ScrollResult<>(reviewVOList, hasMore));
    }
    @Transactional
    @Override
    public Result likeReview(Long reviewId) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是点赞还是取消点赞
        boolean Liked = isLike(reviewId, userId);
        String key = RedisConstants.LIKE_REVIEW_KEY + reviewId;
        if (Liked) {
            // 3.1. 取消点赞
            // 删除数据
            boolean isSuccess = likeRecordService.remove(new QueryWrapper<LikeRecord>()
                    .eq("user_id", userId)
                    .eq("target_id", reviewId)
                    .eq("target_type", SystemConstants.TARGET_REVIEW));
            if(isSuccess){
                // 更新点赞数量
                boolean success=update().setSql("like_count=like_count-1")
                        .eq("id", reviewId)
                        .gt("like_count", 0)
                        .update();
                if(!success){
                    throw new BusinessException("更新点赞数量失败");
                }
                log.info("取消点赞成功");
                // 移除缓存
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
            else{
                log.info("取消点赞失败");
                return Result.fail("取消点赞失败");
            }
            LikeVO likeVO = new LikeVO();
            likeVO.setLike(false);
            likeVO.setLikeCount(getById(reviewId).getLikeCount());
            return Result.ok(likeVO);
        }
        else{
            // 3.2. 点赞
            // 防止点赞不存在的影评
            if(!exists(new QueryWrapper<Review>().eq("id",reviewId).eq("status",SystemConstants.REVIEW_STATUS_NORMAL))){
                return Result.fail("点赞的影评不存在");
            }
            // 防止重复点赞
            boolean exist = likeRecordService.query()
                    .eq("user_id", userId)
                    .eq("target_id", reviewId)
                    .eq("target_type", SystemConstants.TARGET_REVIEW)
                    .exists();
            if(exist){
                return Result.fail("不能重复点赞");
            }
            // 新增数据
            LikeRecord likeRecord = new LikeRecord();
            likeRecord.setUserId(userId);
            likeRecord.setTargetId(reviewId);
            likeRecord.setTargetType(SystemConstants.TARGET_REVIEW);
            boolean isSuccess = likeRecordService.save(likeRecord);
            if (isSuccess) {
                // 4. 更新点赞数量
                boolean success=update().setSql("like_count=like_count+1")
                        .eq("id", reviewId)
                        .update();
                if(!success){
                    throw new BusinessException("更新点赞数量失败");
                }
                log.info("点赞成功");
                // 增添缓存
                stringRedisTemplate.opsForSet().add(key, userId.toString());
                stringRedisTemplate.expire(key, RedisConstants.LIKE_REVIEW_TTL + RandomUtil.randomInt(10), TimeUnit.MINUTES);
                Review review = getById(reviewId);
                if(!Objects.equals(review.getUserId(), userId)){
                    // 发送点赞消息（事务提交后）
                    Long reviewUserId = review.getUserId();
                    MessageDTO dto = new MessageDTO();
                    dto.setUserId(reviewUserId);
                    dto.setFromUserId(userId);
                    dto.setType(SystemConstants.MESSAGE_TYPE_LIKE_REVIEW);
                    dto.setTargetType(SystemConstants.MESSAGE_TARGET_REVIEW);
                    dto.setTargetId(reviewId);
                    dto.setContent("用户" + userService.getById(userId).getNickname() + "点赞了你的影评");
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                rabbitTemplate.convertAndSend(MQConstants.MESSAGE_EXCHANGE, "message.like.review", dto);
                            } catch (Exception e) {
                                log.error("发送点赞影评消息失败: userId={}, reviewId={}", userId, reviewId, e);
                            }
                        }
                    });
                }
            }
            else{
                log.info("点赞失败");
                return Result.fail("点赞失败");
            }
            LikeVO likeVO = new LikeVO();
            likeVO.setLike(true);
            likeVO.setLikeCount(getById(reviewId).getLikeCount());
            return Result.ok(likeVO);
        }
    }
    public boolean isLike(Long reviewId,Long userId) {
        // 2. 查redis
        String reviewKey = RedisConstants.LIKE_REVIEW_KEY + reviewId;
        Boolean exists = stringRedisTemplate.hasKey(reviewKey);
        if (exists) {
            Boolean isLike = stringRedisTemplate.opsForSet()
                    .isMember(reviewKey, userId.toString());

            return Boolean.TRUE.equals(isLike);
        }
        // 3. redis不存在，查数据库重建缓存
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
            stringRedisTemplate.expire(reviewKey, RedisConstants.LIKE_REVIEW_TTL + RandomUtil.randomInt(10), TimeUnit.MINUTES);
        }

        return (ids.contains(userId));
    }
    @Transactional
    @Override
    public Result updateReview(Long reviewId, ReviewDTO reviewDTO) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 确认权限
        Review review = getById(reviewId);
        if(review==null||!Objects.equals(review.getStatus(), SystemConstants.REVIEW_STATUS_NORMAL)){
            return Result.fail("影评不存在");
        }
        Integer oldRating = review.getRating();
        Long movieId = review.getMovieId();
        if (!review.getUserId().equals(userId)) {
            return Result.fail("没有修改权限");
        }
        // 检查
        Result fail = check(reviewDTO);
        if (fail != null) return fail;
        // 3. 修改数据
        boolean isSuccess =update().set("rating", reviewDTO.getRating())
                .set("title", reviewDTO.getTitle())
                .set("content", reviewDTO.getContent())
                .set("spoiler", reviewDTO.getSpoiler())
                .eq("id", reviewId)
                .update();
        if(isSuccess){
            // 修改电影总评分
            int diff = reviewDTO.getRating() - oldRating;
            boolean success=movieService.update()
                    .setSql("rating_sum=rating_sum+" + diff)
                    .eq("id", movieId)
                    .update();
            if(!success){
                throw new BusinessException("更新电影总评分失败");
            }
            log.info("修改成功");
            stringRedisTemplate.delete(RedisConstants.MOVIE_INFO_KEY + movieId);
            syncReviewToES(review);
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
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 确认权限
        Review review = getById(reviewId);
        if(review==null||!Objects.equals(review.getStatus(), SystemConstants.REVIEW_STATUS_NORMAL)){
            return Result.fail("影评不存在");
        }
        Integer oldRating = review.getRating();
        Long movieId = review.getMovieId();
        if (!review.getUserId().equals(userId)) {
            return Result.fail("没有删除权限");
        }
        // 3. 修改数据
        boolean isSuccess = removeById(reviewId);
        if(isSuccess){
            // 修改电影数据
            boolean success1=movieService.update()
                    .setSql("rating_sum=rating_sum-" + oldRating)
                    .setSql("rating_count = rating_count-1")
                    .gt("rating_count", 0)
                    .eq("id", movieId)
                    .update();
            // 修改个人数据
            boolean success2=userService.update()
                    .setSql("review_count=review_count-1")
                    .gt("review_count", 0)
                    .eq("id", userId)
                    .update();
            if(!success1||!success2){
                throw new BusinessException("更新关联数据失败");
            }
            // 删除点赞数据和缓存（可能没有点赞记录，不检查返回值）
            likeRecordService.remove(
                    new QueryWrapper<LikeRecord>()
                            .eq("target_id", reviewId)
                            .eq("target_type", SystemConstants.TARGET_REVIEW)
            );
            // 级联删除该影评下的所有正常评论及其点赞数据
            List<ReviewComment> comments = reviewCommentMapper.selectList(
                    new QueryWrapper<ReviewComment>().eq("review_id", reviewId)
            );
            if (!comments.isEmpty()) {
                Set<Long> commentIds = comments.stream()
                        .map(ReviewComment::getId)
                        .collect(Collectors.toSet());
                likeRecordService.remove(
                        new QueryWrapper<LikeRecord>()
                                .in("target_id", commentIds)
                                .eq("target_type", SystemConstants.TARGET_COMMENT)
                );
                commentIds.forEach(id ->
                        stringRedisTemplate.delete(RedisConstants.LIKE_COMMENT_KEY + id)
                );
                int deleted = reviewCommentMapper.delete(
                        new QueryWrapper<ReviewComment>().eq("review_id", reviewId)
                );
                if (deleted == 0) {
                    throw new BusinessException("级联删除评论失败");
                }
            }
            // 将影评likeCount和commentCount清零
            isSuccess=update().set("like_count", 0).set("comment_count", 0).eq("id", reviewId).update();
            if(!isSuccess){
                throw new BusinessException("更新关联数据失败");
            }
            stringRedisTemplate.delete(RedisConstants.LIKE_REVIEW_KEY + reviewId);
            stringRedisTemplate.opsForZSet().remove(RedisConstants.HOT_REVIEW_KEY,reviewId.toString());
            stringRedisTemplate.delete(RedisConstants.MOVIE_INFO_KEY + movieId);
            try { reviewSearchRepository.deleteById(reviewId); } catch (Exception e) { log.error("ES 删除影评文档失败: reviewId={}", reviewId, e); }
            log.info("删除成功");
            return Result.ok();
        }
        else{
            log.info("删除失败");
            return Result.fail("删除失败");
        }
    }

    @Override
    public Result hotReviews(Integer current) {
        if(current==null||current<=0){
            current = 1;
        }
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询redis
        String key = RedisConstants.HOT_REVIEW_KEY;
        int size = SystemConstants.MAX_PAGE_SIZE;
        long start = (long) (current - 1) * size;
        long end = start + size - 1;
        Set<String> setIds = stringRedisTemplate.opsForZSet().reverseRange(key, start,end);
        if(setIds == null || setIds.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> listIds = setIds.stream()
                .map(Long::valueOf)
                .toList();
        List<Review> reviewList = listByIds(listIds);
        // 保持redis排名顺序
        Map<Long, Review> reviewMap = reviewList.stream()
                .collect(Collectors.toMap(
                        Review::getId,
                        Function.identity()
                ));
        List<Review> sortReviewList = listIds.stream()
                .map(reviewMap::get)
                .filter(Objects::nonNull)
                .toList();
        // 3. 获取用户id和影评id
        Set<Long> userIds = getUserIds(sortReviewList);
        Set<Long> reviewIds = getReviewIds(sortReviewList);
        // 4. 批量查询用户
        Map<Long, User> userMap = getUserMap(userIds);
        // 5. 查询当前用户点赞过的影评
        Set<Long> likeReviewIds = getLikeReviewIds(userId, reviewIds);
        // 6. 批量查询电影
        Map<Long, Movie> movieMap = getMovieMap(sortReviewList);
        // 7. 转化为VO
        List<ReviewVO> reviewVOList = getReviewVOList(sortReviewList, userMap, likeReviewIds, userId, movieMap);
        // 8. 判断是否还有下一页
        boolean hasMore = setIds.size() == size;
        return Result.ok(new ScrollResult<>(reviewVOList,hasMore));
    }
    @Override
    public void updateHotReviewCache() {
        // 1. 查询最近30天影评
        List<Review> reviews= lambdaQuery()
                .gt(
                        Review::getCreateTime,
                        LocalDateTime.now().minusDays(30)
                )
                .eq(Review::getStatus, SystemConstants.REVIEW_STATUS_NORMAL)
                .list();
        // 2. 计算score并排序
        List<ReviewHotDTO> hotReviews = reviews.stream()
                .map(review -> {
                    ReviewHotDTO dto = new ReviewHotDTO();
                    dto.setReviewId(review.getId());
                    long hours = ChronoUnit.HOURS.between(
                            review.getCreateTime(),
                            LocalDateTime.now()
                    );
                    double score = (review.getLikeCount()*10 + review.getCommentCount()*5+20) / Math.sqrt(hours+2);
                    dto.setScore(score);
                    return dto;
                })
                .sorted(
                        Comparator.comparing(
                                ReviewHotDTO::getScore
                        ).reversed()
                )
                .limit(100)
                .toList();
        // 3. 写入redis
        String oldKey = RedisConstants.HOT_REVIEW_KEY;
        String newKey = RedisConstants.HOT_REVIEW_KEY + ":temp";
        stringRedisTemplate.delete(newKey);
        for(ReviewHotDTO review:hotReviews){
            stringRedisTemplate.opsForZSet()
                    .add(
                            newKey,
                            review.getReviewId().toString(),
                            review.getScore()
                    );
        }
        stringRedisTemplate.rename(newKey, oldKey);
        log.info("更新热门影评缓存成功");
    }
    private List<ReviewVO> getReviewVOList(List<Review> reviewList, Map<Long, User> userMap, Set<Long> likeReviewIds, Long userId, Map<Long, Movie> movieMap) {
        return reviewList.stream()
                .map(
                        review -> {
                            ReviewVO vo = new ReviewVO();
                            BeanUtils.copyProperties(review, vo);
                            User user = userMap.get(review.getUserId());
                            if (user != null) {
                                vo.setUserName(user.getUsername());
                                vo.setNickName(user.getNickname());
                                vo.setAvatar(user.getAvatar());
                            }
                            Movie movie = movieMap.get(review.getMovieId());
                            if (movie != null) {
                                vo.setMovieId(movie.getId());
                                vo.setMovieTitle(movie.getTitle());
                            }
                            vo.setIsLike(
                                    likeReviewIds.contains(review.getId())
                            );
                            vo.setCanEditAndDelete(
                                    review.getUserId().equals(userId)
                            );
                            return vo;
                        }).toList();
    }
    private Map<Long, Movie> getMovieMap(List<Review> reviewList) {
        // 批量查询电影
        Set<Long> movieIds = reviewList.stream().map(Review::getMovieId).collect(Collectors.toSet());
        return movieService.listByIds(movieIds)
                .stream()
                .collect(Collectors.toMap(
                        Movie::getId, m -> m
                ));
    }

    private Map<Long, User> getUserMap(Set<Long> userIds) {
        return userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user
                ));
    }
    private static Set<Long> getUserIds(List<Review> reviewList) {
        return reviewList.stream()
                .map(Review::getUserId)
                .collect(Collectors.toSet());
    }
    private static Set<Long> getReviewIds(List<Review> reviewList) {
        return reviewList.stream()
                .map(Review::getId)
                .collect(Collectors.toSet());
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
    private static Result check(ReviewDTO reviewDTO) {
        if(reviewDTO.getRating() == null|| reviewDTO.getRating() < 1 || reviewDTO.getRating() > 10){
            return Result.fail("评分必须在1-10之间");
        }
        if(reviewDTO.getContent() == null || reviewDTO.getContent().trim().isEmpty() || reviewDTO.getContent().trim().length() > 1000){
            return Result.fail("内容长度必须在1-1000之间");
        }
        if(reviewDTO.getSpoiler() != null && (reviewDTO.getSpoiler() < 0 || reviewDTO.getSpoiler() > 1)){
            return Result.fail("剧透参数无效");
        }
        if(reviewDTO.getTitle() == null || reviewDTO.getTitle().trim().isEmpty() || reviewDTO.getTitle().trim().length() > 50){
            return Result.fail("标题长度必须在1-50之间");
        }
        return null;
    }

    @Override
    public Result searchReviews(String keyword, Integer current, Integer spoiler) {
        if (StrUtil.isBlank(keyword)) {
            return Result.ok(new ScrollResult<>(Collections.emptyList(), false));
        }
        if (current == null || current < 1) {
            current = 1;
        }
        int size = SystemConstants.MAX_PAGE_SIZE;
        Long userId = UserHolder.getUser().getId();
        try {
            BoolQuery.Builder bool = new BoolQuery.Builder()
                    .must(Query.of(q -> q.multiMatch(mm -> mm
                            .query(keyword)
                            .fields("title^3", "movieTitle^2", "content")
                    )))
                    .filter(Query.of(q -> q.term(t -> t.field("status").value(1))));
            if (spoiler != null) {
                bool.filter(Query.of(q -> q.term(t -> t.field("spoiler").value(spoiler))));
            }
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(bool.build()))
                    .withPageable(PageRequest.of(current - 1, size + 1))
                    .build();
            SearchHits<ReviewDocument> hits = elasticsearchOperations.search(query, ReviewDocument.class);
            List<Long> ids = hits.getSearchHits().stream()
                    .map(h -> h.getContent().getId())
                    .toList();
            if (ids.isEmpty()) {
                return Result.ok(new ScrollResult<>(Collections.emptyList(), false));
            }
            boolean hasMore = ids.size() > size;
            if (hasMore) {
                ids = ids.subList(0, size);
            }
            Map<Long, Review> reviewMap = listByIds(ids).stream()
                    .collect(Collectors.toMap(Review::getId, Function.identity()));
            Set<Long> likeIds = getLikeReviewIds(userId, new HashSet<>(ids));
            Set<Long> userIds = reviewMap.values().stream().map(Review::getUserId).collect(Collectors.toSet());
            Map<Long, User> userMap = getUserMap(userIds);
            Map<Long, Movie> movieMap = getMovieMap(new ArrayList<>(reviewMap.values()));
            List<ReviewVO> vos = getReviewVOList(
                    ids.stream().map(reviewMap::get).filter(Objects::nonNull).toList(),
                    userMap, likeIds, userId, movieMap
            );
            return Result.ok(new ScrollResult<>(vos, hasMore));
        } catch (Exception e) {
            log.error("ES 搜索影评失败，降级为 MySQL LIKE 查询: keyword={}", keyword, e);
        }
        Page<Review> page = query()
                .and(w -> w.like("title", keyword).or().like("content", keyword))
                .eq(spoiler != null, "spoiler", spoiler)
                .eq("status", SystemConstants.REVIEW_STATUS_NORMAL)
                .orderByDesc("like_count")
                .orderByDesc("create_time")
                .page(new Page<>(current, size + 1));
        List<Review> reviewList = page.getRecords();
        if (reviewList.isEmpty()) {
            return Result.ok(new ScrollResult<>(Collections.emptyList(), false));
        }
        boolean hasMore = reviewList.size() > size;
        if (hasMore) {
            reviewList = reviewList.subList(0, size);
        }
        List<ReviewVO> vos = buildReviewVOS(reviewList, userId);
        return Result.ok(new ScrollResult<>(vos, hasMore));
    }

    private  List<ReviewVO> buildReviewVOS (List<Review> reviewList, Long userId) {
        Set<Long> reviewIds = getReviewIds(reviewList);
        Set<Long> userIds = getUserIds(reviewList);
        Map<Long, User> userMap = getUserMap(userIds);
        Map<Long, Movie> movieMap = getMovieMap(reviewList);
        Set<Long> likeReviewIds = getLikeReviewIds(userId, reviewIds);
        return getReviewVOList(reviewList, userMap, likeReviewIds, userId, movieMap);
    }

    private void syncReviewToES(Review review) {
        try {
            ReviewDocument doc = BeanUtil.copyProperties(review, ReviewDocument.class);
            Movie movie = movieService.getById(review.getMovieId());
            if (movie != null) {
                doc.setMovieTitle(movie.getTitle());
            }
            reviewSearchRepository.save(doc);
        } catch (Exception e) {
            log.error("ES 同步影评文档失败: reviewId={}", review.getId(), e);
        }
    }
}
