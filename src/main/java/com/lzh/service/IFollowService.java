package com.lzh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lzh.common.Result;
import com.lzh.po.UserFollow;

public interface IFollowService extends IService<UserFollow> {
    Result getFollowerList();

    Result getFollowingList();

    Result follow(Long id, Boolean isFollow);

    Result isFollow(Long id);
}
