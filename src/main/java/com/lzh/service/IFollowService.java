package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.po.UserFollow;

public interface IFollowService extends IService<UserFollow> {
    Result getFollowerList(Integer current);

    Result getFollowingList(Integer current);

    Result follow(Long id, Boolean isFollow);

    Result isFollow(Long id);
}
