package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.dto.MessageDTO;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.FollowMapper;
import com.lzh.po.User;
import com.lzh.po.UserFollow;
import com.lzh.service.IFollowService;
import com.lzh.service.IUserService;
import com.lzh.utils.MQConstants;
import com.lzh.utils.RedisConstants;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, UserFollow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result getFollowerList(Integer current) {
        // 1. 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 2. 查redis ZSet
        String key = RedisConstants.FOLLOWER_KEY + userId;
        Result redisResult = tryGetListFromRedis(key, current);
        if (redisResult != null) {
            return redisResult;
        }
        // redis没数据
        // 3. 查数据库重建缓存
        Page<UserFollow> pageResult = query()
                .eq("follow_user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        if (pageResult.getTotal() > 0) {
            // 重建全量缓存
            List<UserFollow> follows = query()
                    .eq("follow_user_id", userId)
                    .orderByDesc("create_time")
                    .list();
            for (UserFollow f : follows) {
                stringRedisTemplate.opsForZSet().add(key,
                        f.getUserId().toString(),
                        f.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWER_TTL, TimeUnit.MINUTES);

            List<Long> ids = pageResult.getRecords().stream()
                    .map(UserFollow::getUserId).toList();
            return Result.ok(new PageResult<>(pageResult.getTotal(), getUserDTOS(ids)));
        } else {
            stringRedisTemplate.opsForZSet().add(key, "empty", 0);
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWER_EMPTY_TTL, TimeUnit.MINUTES);
            return Result.ok(new PageResult<UserDTO>(0L, Collections.emptyList()));
        }
    }
    @Override
    public Result getFollowingList(Integer current) {
        // 1. 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 2. 查redis ZSet
        String key = RedisConstants.FOLLOWING_KEY + userId;
        Result redisResult = tryGetListFromRedis(key, current);
        if (redisResult != null) {
            return redisResult;
        }
        // redis没数据
        // 3. 查数据库重建缓存
        Page<UserFollow> pageResult = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        if (pageResult.getTotal() > 0) {
            // 重建全量缓存
            List<UserFollow> follows = query()
                    .eq("user_id", userId)
                    .orderByDesc("create_time")
                    .list();
            for (UserFollow f : follows) {
                stringRedisTemplate.opsForZSet().add(key,
                        f.getFollowUserId().toString(),
                        f.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_TTL, TimeUnit.MINUTES);

            List<Long> ids = pageResult.getRecords().stream()
                    .map(UserFollow::getFollowUserId).toList();
            return Result.ok(new PageResult<>(pageResult.getTotal(), getUserDTOS(ids)));
        } else {
            stringRedisTemplate.opsForZSet().add(key, "empty", 0);
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_EMPTY_TTL, TimeUnit.MINUTES);
            return Result.ok(new PageResult<UserDTO>(0L, Collections.emptyList()));
        }
    }
    private List<UserDTO> getUserDTOS(List<Long> ids) {
        List<User> users = userService.listByIds(ids);
        Map<Long, UserDTO> map = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> BeanUtil.copyProperties(user, UserDTO.class),
                        (existing, replacement) -> existing  // 遇到重复 key，保留第一个
                ));

        return ids.stream()
                .map(map::get)
                .filter(Objects::nonNull) // 排除关注关系存在，但用户已被删除的脏数据
                .toList();
    }
    private Result tryGetListFromRedis(String key, Integer current) {
        try {
            Long total = stringRedisTemplate.opsForZSet().size(key);
            if (total != null && total > 0) {
                boolean hasEmpty = stringRedisTemplate.opsForZSet().score(key, "empty") != null;
                long realTotal = total - (hasEmpty ? 1 : 0);
                if (realTotal == 0) {
                    return Result.ok(new PageResult<UserDTO>(0L, Collections.emptyList()));
                }
                int start = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
                int end = start + SystemConstants.MAX_PAGE_SIZE - 1;
                Set<String> idSet = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
                if (idSet == null) {
                    return Result.ok(new PageResult<UserDTO>(0L, Collections.emptyList()));
                }
                List<Long> ids = idSet.stream()
                        .filter(s -> !"empty".equals(s))
                        .map(Long::valueOf)
                        .toList();
                return Result.ok(new PageResult<>(realTotal, getUserDTOS(ids)));
            }
        } catch (Exception e) {
            // redis格式不兼容，删掉走DB
            stringRedisTemplate.delete(key);
        }
        return null;
    }

    @Transactional
    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 1. 获取当前用户id并判断是否为自己
        Long userId = UserHolder.getUser().getId();
        if (userId.equals(id)) {
            return Result.fail("不能关注自己");
        }
        // 2. 判断关注还是取关
        if (!isFollow) {
            // 防止关注不存在的用户
            if (!userService.exists(new QueryWrapper<User>().eq("id", id).eq("status", SystemConstants.USER_STATUS_NORMAL))) {
                return Result.fail("用户不存在");
            }
            // 防止重复关注
            boolean exist = query()
                    .eq("user_id", userId)
                    .eq("follow_user_id", id)
                    .exists();
            if (exist) {
                return Result.fail("不能重复关注");
            }
            // 3.1. 关注，新增数据
            UserFollow userFollow = new UserFollow();
            userFollow.setUserId(userId);
            userFollow.setFollowUserId(id);
            boolean isSuccess = save(userFollow);

            if (isSuccess) {
                // 增加关注者的关注数和被关注者的粉丝数
                boolean isSuccess1 =userService.update()
                        .setSql("following_count=following_count+1")
                        .eq("id", userId)
                        .update();
                boolean isSuccess2 =userService.update()
                        .setSql("follower_count=follower_count+1")
                        .eq("id", id)
                        .update();
                if(!isSuccess1||!isSuccess2){
                     throw new RuntimeException("关注失败");
                }
                log.info("关注成功");
                // 增添缓存
                long now = System.currentTimeMillis();
                stringRedisTemplate.opsForZSet().add(RedisConstants.FOLLOWER_KEY + id, userId.toString(), now);
                stringRedisTemplate.opsForZSet().remove(RedisConstants.FOLLOWER_KEY + id, "empty");
                stringRedisTemplate.expire(RedisConstants.FOLLOWER_KEY + id, RedisConstants.FOLLOWER_TTL, TimeUnit.MINUTES);
                stringRedisTemplate.opsForZSet().add(RedisConstants.FOLLOWING_KEY + userId, id.toString(), now);
                stringRedisTemplate.opsForZSet().remove(RedisConstants.FOLLOWING_KEY + userId, "empty");
                stringRedisTemplate.expire(RedisConstants.FOLLOWING_KEY + userId, RedisConstants.FOLLOWING_TTL, TimeUnit.MINUTES);
                // 发送关注消息
                try {
                    MessageDTO dto = new MessageDTO();
                    dto.setUserId(id);
                    dto.setFromUserId(userId);
                    dto.setType(SystemConstants.MESSAGE_TYPE_FOLLOW);
                    dto.setTargetType(SystemConstants.MESSAGE_TARGET_USER);
                    dto.setTargetId(userId);
                    String content = "用户" + userService.getById(userId).getNickname() + "关注了你";
                    dto.setContent(content);
                    rabbitTemplate.convertAndSend(MQConstants.MESSAGE_EXCHANGE, "message.follow", dto);
                } catch (AmqpException e) {
                    log.error("发送消息失败");
                    throw new RuntimeException(e);
                }
            }
            else{
                log.info("关注失败");
                return Result.fail("关注失败");
            }
        } else {
            // 3.2. 取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<UserFollow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", id));
            if (isSuccess) {
                // 减少关注者的关注数和被关注者的粉丝数
                boolean isSuccess1 =userService.update()
                        .setSql("following_count=following_count-1")
                        .gt("following_count", 0)
                        .eq("id", userId)
                        .update();
                boolean isSuccess2 =userService.update()
                        .setSql("follower_count=follower_count-1")
                        .gt("follower_count", 0)
                        .eq("id", id)
                        .update();
                if(!isSuccess1||!isSuccess2){
                    throw new RuntimeException("取关失败");
                }
                log.info("取关成功");
                // 移除缓存
                stringRedisTemplate.opsForZSet().remove(RedisConstants.FOLLOWER_KEY + id, userId.toString());
                stringRedisTemplate.opsForZSet().remove(RedisConstants.FOLLOWING_KEY + userId, id.toString());
            }
            else{
                log.info("取关失败");
                return Result.fail("取关失败");
            }
        }
        return Result.ok();
    }
    @Override
    public Result isFollow(Long id) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查redis
        String key = RedisConstants.FOLLOWING_KEY + userId;
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (exists) {
            try {
                Double score = stringRedisTemplate.opsForZSet().score(key, id.toString());
                return Result.ok(score != null);
            } catch (Exception e) {
                // redis格式不兼容，删掉走DB
                stringRedisTemplate.delete(key);
            }
        }

        // 3. redis不存在，查询数据库重建缓存
        List<UserFollow> follows = query()
                .eq("user_id", userId)
                .list();

        Set<Long> followUserIds = follows.stream()
                .map(UserFollow::getFollowUserId)
                .collect(Collectors.toSet());

        if (!follows.isEmpty()) {
            for (UserFollow f : follows) {
                stringRedisTemplate.opsForZSet().add(key,
                        f.getFollowUserId().toString(),
                        f.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_TTL, TimeUnit.MINUTES);
        } else {
            stringRedisTemplate.opsForZSet().add(key, "empty", 0);
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_EMPTY_TTL, TimeUnit.MINUTES);
        }

        return Result.ok(followUserIds.contains(id));
    }
}