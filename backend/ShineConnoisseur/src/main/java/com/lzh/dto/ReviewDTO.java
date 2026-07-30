package com.lzh.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "影评表单")
public class ReviewDTO {
    @Schema(description = "评分（1-10）")
    private Integer rating;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "内容")
    private String content;
    @Schema(description = "是否含剧透（0=否, 1=是）")
    private Integer spoiler;
}
