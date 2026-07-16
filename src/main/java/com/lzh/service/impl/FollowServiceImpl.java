package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Result getFollowerList() {
        // 1. 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 2. 查redis
        String key=RedisConstants.FOLLOWER_KEY + userId;
        Set<String> setIds = stringRedisTemplate.opsForSet().members(key);
        if(setIds!= null&& !setIds.isEmpty()){
            List<Long> ids = setIds.stream()
                    .filter(s -> !"empty".equals(s))
                    .map(Long::valueOf)
                    .toList();
            if (ids.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            else{
                return Result.ok(getUserDTOS(ids));
            }
        }
        // redis没数据
        // 3. 查数据库重建缓存
        List<Long> ids = query()
                .eq("follow_user_id", userId)
                .list()
                .stream()
                .map(UserFollow::getUserId)
                .toList();

        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(key, values);
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWER_TTL, TimeUnit.MINUTES);
            return Result.ok(getUserDTOS(ids));
        }
        else{
            stringRedisTemplate.opsForSet().add(key,"empty");
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWER_EMPTY_TTL, TimeUnit.MINUTES);
        }

        return Result.ok(Collections.emptyList());
    }

    @Override
    public Result getFollowingList() {
        // 1. 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 2. 查redis
        String key = RedisConstants.FOLLOWING_KEY + userId;
        Set<String> setIds = stringRedisTemplate.opsForSet().members(key);
        if(setIds!= null&& !setIds.isEmpty()){
            List<Long> ids = setIds.stream()
                    .filter(s -> !"empty".equals(s))
                    .map(Long::valueOf)
                    .toList();
            if (ids.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            else{
                return Result.ok(getUserDTOS(ids));
            }
        }

        // redis没数据
        // 3. 查数据库重建缓存
        List<Long> ids = query()
                .eq("user_id", userId)
                .list()
                .stream()
                .map(UserFollow::getFollowUserId)
                .toList();

        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(key, values);
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_TTL, TimeUnit.MINUTES);
            return Result.ok(getUserDTOS(ids));
        }
        else{
            stringRedisTemplate.opsForSet().add(key,"empty");
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_EMPTY_TTL, TimeUnit.MINUTES);
        }

        return Result.ok(Collections.emptyList());
    }

    private List<UserDTO> getUserDTOS(List<Long> ids) {
        // 查询用户信息
        // 这样会导致返回顺序不一致
        // return userService.listByIds(ids)
        //         .stream()
        //         .map(item -> BeanUtil.copyProperties(item, UserDTO.class))
        //         .toList();

        // 优化，保证返回顺序一致
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

    @Transactional
    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 防止关注不存在的用户
        if (!userService.exists(new QueryWrapper<User>().eq("id", id).eq("status", SystemConstants.USER_STATUS_NORMAL))) {
            return Result.fail("用户不存在");
        }
        // 1. 获取当前用户id并判断是否为自己
        Long userId = UserHolder.getUser().getId();
        if (userId.equals(id)) {
            return Result.fail("不能关注自己");
        }
        // 2. 判断关注还是取关
        if (!isFollow) {
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
                // 移除缓存
                stringRedisTemplate.delete(RedisConstants.FOLLOWER_KEY + id);
                stringRedisTemplate.delete(RedisConstants.FOLLOWING_KEY + userId);
                // 发送关注消息
                MessageDTO dto = new MessageDTO();
                dto.setUserId(id);
                dto.setFromUserId(userId);
                dto.setType(SystemConstants.MESSAGE_TYPE_FOLLOW);
                dto.setTargetType(SystemConstants.MESSAGE_TARGET_USER);
                dto.setTargetId(userId);
                String content = "用户" + userService.getById(userId).getNickname() + "关注了你";
                dto.setContent(content);
                rabbitTemplate.convertAndSend(MQConstants.MESSAGE_EXCHANGE, "message.follow", dto);
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
                        .eq("id", userId)
                        .update();
                boolean isSuccess2 =userService.update()
                        .setSql("follower_count=follower_count-1")
                        .eq("id", id)
                        .update();
                if(!isSuccess1||!isSuccess2){
                    throw new RuntimeException("取关失败");
                }
                log.info("取关成功");
                // 移除缓存
                stringRedisTemplate.delete(RedisConstants.FOLLOWER_KEY + id);
                stringRedisTemplate.delete(RedisConstants.FOLLOWING_KEY + userId);
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
            Boolean isFollow = stringRedisTemplate.opsForSet()
                    .isMember(key, id.toString());

            return Result.ok(Boolean.TRUE.equals(isFollow));
        }

        // 3. redis不存在，查询数据库重建缓存
        List<Long> ids = query()
                .eq("user_id", userId)
                .list()
                .stream()
                .map(UserFollow::getFollowUserId)
                .toList();

        if (!ids.isEmpty()) {
            String[] values = ids.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(key, values);
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_TTL, TimeUnit.MINUTES);
        }
        else{
            stringRedisTemplate.opsForSet().add(key,"empty");
            stringRedisTemplate.expire(key, RedisConstants.FOLLOWING_EMPTY_TTL, TimeUnit.MINUTES);
        }

        return Result.ok(ids.contains(id));
    }
}