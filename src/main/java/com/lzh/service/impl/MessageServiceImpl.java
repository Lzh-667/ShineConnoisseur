package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lzh.common.PageResult;
import com.lzh.common.Result;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.MessageMapper;
import com.lzh.po.Message;
import com.lzh.po.User;
import com.lzh.service.IMessageService;
import com.lzh.service.IUserService;
import com.lzh.utils.SystemConstants;
import com.lzh.utils.UserHolder;
import com.lzh.vo.MessageVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements IMessageService {

    @Resource
    private IUserService userService;
    @Override
    public Result listAll(Long current, Integer type) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.分页查询
        Page<Message> page = query()
                .eq("user_id", userId)
                .eq(type != null,"type",type)
                .orderByDesc("create_time")
                .page(new Page<>(current, 10));
        //3.提取发送用户id
        List<Long> fromUserIds = page.getRecords()
                .stream()
                .map(Message::getFromUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        //4.批量查询用户
        List<User> users = userService.listByIds(fromUserIds);
        //5.转换Map方便获取
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user
                ));
        //6.包装VO
        List<MessageVO> vos = page.getRecords()
                .stream()
                .map(message -> {
                    MessageVO vo = new MessageVO();
                    BeanUtils.copyProperties(message, vo);
                    //查询发送人
                    UserDTO fromUserDTO = BeanUtil.copyProperties(userMap.get(message.getFromUserId()), UserDTO.class);
                    vo.setFromUser(fromUserDTO);
                    return vo;
                }).toList();

        //7.封装并返回
        PageResult<MessageVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(vos);
        return Result.ok(result);
    }
    @Override
    public Result unreadCount() {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询并返回
        Long count = query()
                .eq("user_id", userId)
                .eq("status", SystemConstants.MESSAGE_STATUS_UNREAD)
                .count();
        return Result.ok(count);
    }
    @Transactional
    @Override
    public Result read(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.修改状态
        boolean update = lambdaUpdate()
                .eq(Message::getId,id)
                .eq(Message::getUserId,userId)
                .set(Message::getStatus,SystemConstants.MESSAGE_STATUS_READ)
                .update();

        if(!update){
            return Result.fail("消息不存在或无权限");
        }
        return Result.ok();
    }
    @Transactional
    @Override
    public Result readAll() {
        Long userId = UserHolder.getUser().getId();
        LambdaUpdateWrapper<Message> wrapper =
                new LambdaUpdateWrapper<>();
        wrapper.eq(Message::getUserId,userId)
                .eq(Message::getStatus,SystemConstants.MESSAGE_STATUS_UNREAD)
                .set(Message::getStatus,SystemConstants.MESSAGE_STATUS_READ);
        boolean update = update(wrapper);
        if(!update){
            return Result.fail("消息不存在或无权限");
        }
        return Result.ok();
    }
}
