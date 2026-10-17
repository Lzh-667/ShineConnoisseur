package com.lzh.service;

import com.lzh.dto.UserDTO;
import java.util.List;
import java.util.Set;
import java.util.Collection;

/** 会员身份查询契约。 */
public interface VipIdentityService {
    boolean isVip(Long userId);

    Set<Long> activeUserIds(Collection<Long> userIds);

    void enrich(List<UserDTO> users);
}
