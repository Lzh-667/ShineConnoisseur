package com.lzh.dto;

import lombok.Data;

@Data
public class ReviewCommentDTO {

    /** 一级评论 */
    private Long rootId=0L;

    /** 回复评论用户id */
    private Long replyUserId;

    /** 评论内容 */
    private String content;

}
