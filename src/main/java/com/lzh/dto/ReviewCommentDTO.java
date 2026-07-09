package com.lzh.dto;

import lombok.Data;

@Data
public class ReviewCommentDTO {

    /** 父级评论 0表示不存在父级评论*/
    private Long parentId=0L;

    /** 评论内容 */
    private String content;

}
