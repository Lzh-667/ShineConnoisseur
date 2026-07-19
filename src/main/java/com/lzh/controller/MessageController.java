package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.service.IMessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/messages")
@RestController
public class MessageController {

    @Resource
    private IMessageService messageService;
    @GetMapping("/list")
    public Result listAll(
            @RequestParam(value = "current", defaultValue = "1") Long current,
            @RequestParam(value = "type", required = false) Integer type) {
        return messageService.listAll(current,type);
    }
    @GetMapping("/unread/count")
    public Result unreadCount() {
        return messageService.unreadCount();
    }
    @PutMapping("/read/{id}")
    public Result read(@PathVariable Long id){
        return messageService.read(id);
    }
    @PutMapping("/read/all")
    public Result readAll(){
        return messageService.readAll();
    }
}
