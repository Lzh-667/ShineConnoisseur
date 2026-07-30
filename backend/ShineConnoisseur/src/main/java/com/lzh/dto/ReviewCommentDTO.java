package com.lzh.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "评论表单")
public class ReviewCommentDTO {
    @Schema(description = "根评论ID（0=顶级评论）")
    private Long rootId = 0L;
    @Schema(description = "被回复用户ID（0=非回复）")
    private Long replyUserId = 0L;
    @Schema(description = "评论内容")
    private String content;
}
