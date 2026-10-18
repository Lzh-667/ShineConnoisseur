package com.lzh.controller.admin;

import com.lzh.common.Result;
import com.lzh.service.MessageDeadLetterService;
import com.lzh.service.MessageFailureService;
import com.lzh.dto.MessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admins/messages")
@RequiredArgsConstructor
public class AdminMessageController {
    private final MessageDeadLetterService deadLetterService;
    private final MessageFailureService failures;

    @PostMapping("/dead-letters/replay")
    public Result replay(@RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) return Result.fail("limit 必须在 1 到 100 之间");
        return Result.ok(deadLetterService.replay(limit));
    }

    @GetMapping("/failures")
    public Result failures(@RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "20") int limit) {
        if (afterId < 0 || limit < 1 || limit > 100) return Result.fail("分页参数不合法");
        return Result.ok(failures.list(afterId, limit));
    }

    @GetMapping("/failures/{id}")
    public Result failure(@PathVariable long id) {
        var record = failures.find(id);
        return record == null ? Result.fail("记录不存在") : Result.ok(record);
    }

    @PostMapping("/failures/{id}/replay")
    public Result replayFailure(@PathVariable long id, @RequestBody(required = false) MessageDTO replacement) {
        if (replacement != null) {
            try { com.lzh.service.MessageOutboxService.validate(replacement); }
            catch (IllegalArgumentException e) { return Result.fail(e.getMessage()); }
        }
        return failures.replay(id, replacement) ? Result.ok() : Result.fail("记录不存在、已重放或需要修正内容");
    }
}
