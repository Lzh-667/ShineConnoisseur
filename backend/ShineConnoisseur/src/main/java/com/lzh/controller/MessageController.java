package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.IMessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RequestMapping("/messages")
@RestController
@Tag(name = "消息模块", description = "站内消息列表、未读计数、已读标记")
public class MessageController {

    @Resource
    private IMessageService messageService;
    @Operation(summary = "消息列表")
    @GetMapping("/list")
    public Result listAll(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "type", required = false) Integer type) {
        return messageService.listAll(current,type);
    }
    @Operation(summary = "未读消息数")
    @GetMapping("/unread/count")
    public Result unreadCount() {
        return messageService.unreadCount();
    }
    @Operation(summary = "标记单条已读")
    @PutMapping("/read/{id}")
    public Result read(@PathVariable Long id){
        return messageService.read(id);
    }
    @Operation(summary = "全部标记已读")
    @PutMapping("/read/all")
    public Result readAll(){
        return messageService.readAll();
    }
}
