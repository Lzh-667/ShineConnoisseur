package com.lzh.vo;

import com.lzh.dto.UserDTO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewCommentVO {

    /** 评论ID */
    private Long id;

    /** 作者信息 */
    private UserDTO author;

    /** 被回复的人信息 */
    private UserDTO replyUser;

    /** 一级评论 */
    private Long rootId;

    /** 评论内容 */
    private String content;

    /** 是否可以编辑 */
    private Boolean canEditAndDelete;

    /** 当前用户是否点赞 */
    private Boolean isLike;

    /** 点赞数 */
    private Integer likeCount=0;

    /** 创建时间 */
    private LocalDateTime createTime;

}
