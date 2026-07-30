package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.IFollowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/follows")
@Tag(name = "关注模块", description = "关注/取关、粉丝列表、关注列表")
public class FollowController {

    @Resource
    private IFollowService followService;

    @Operation(summary = "粉丝列表")
    @GetMapping("/list/follower")
    public Result getFollowerList(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.getFollowerList(current);
    }
    @Operation(summary = "关注列表")
    @GetMapping("/list/following")
    public Result getFollowingList(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.getFollowingList(current);
    }
    @Operation(summary = "关注/取关")
    @PostMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(id, isFollow);
    }
    @Operation(summary = "检查是否关注")
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }
}
