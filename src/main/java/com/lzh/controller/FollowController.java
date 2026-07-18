package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.IFollowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/follows")
public class FollowController {

    @Resource
    private IFollowService followService;

    @GetMapping("/list/follower")
    public Result getFollowerList() {
        return followService.getFollowerList();
    }
    @GetMapping("/list/following")
    public Result getFollowingList() {
        return followService.getFollowingList();
    }
    @PostMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(id, isFollow);
    }
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }
}
