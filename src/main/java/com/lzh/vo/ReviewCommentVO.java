package com.lzh.vo;

import lombok.Data;

@Data
public class ReviewCommentVO {

    /** 评论ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 一级评论 0表示不存在一级评论*/
    private Long rootId;

    /** 被回复用户 0表示不存在一级评论*/
    private Long replyUserId;

    /** 昵称 */
    private String nickName;

    /** 作者头像 */
    private String avatar;

    /** 评论内容 */
    private String content;

    /** 是否可以编辑 */
    private Boolean canEditAndDelete;

    /** 当前用户是否点赞 */
    private Boolean isLike;

    /** 点赞数 */
    private Integer likeCount=0;

    /** 回复数量 */
    private Integer replyCount=0;

}
