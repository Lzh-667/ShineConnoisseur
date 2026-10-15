package com.lzh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.UserVipMapper;
import com.lzh.po.UserVip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * VIP 身份查询入口。所有社区身份标识都以会员到期时间为准，避免前端自行判断。
 */
@Service
@RequiredArgsConstructor
public class VipIdentityService {

    private final UserVipMapper userVipMapper;

    public boolean isVip(Long userId) {
        return userId != null && activeUserIds(List.of(userId)).contains(userId);
    }

    public Set<Long> activeUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> validIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (validIds.isEmpty()) {
            return Collections.emptySet();
        }
        return userVipMapper.selectList(
                        new LambdaQueryWrapper<UserVip>()
                                .select(UserVip::getUserId)
                                .in(UserVip::getUserId, validIds)
                                .gt(UserVip::getExpireTime, LocalDateTime.now())
                ).stream()
                .map(UserVip::getUserId)
                .collect(Collectors.toSet());
    }

    public void enrich(List<UserDTO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<UserDTO> validUsers = users.stream()
                .filter(Objects::nonNull)
                .toList();
        if (validUsers.isEmpty()) {
            return;
        }
        Set<Long> vipUserIds = activeUserIds(validUsers.stream().map(UserDTO::getId).toList());
        validUsers.forEach(user -> user.setVip(vipUserIds.contains(user.getId())));
    }
}
