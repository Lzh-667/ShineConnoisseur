package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**基础信息*/
    private String username;
    private String password;
    private String email;
    private String phone;
    private String nickname;
    private String avatar;

    /** 性别：0-保密，1-男，2-女 */
    private Integer gender;

    /** 个性签名 */
    private String bio;

    /** 状态：0-禁用，1-正常 */
    private Integer status;

    /** 影评数 */
    @TableField("review_count")
    private Integer reviewCount;
    /** 粉丝数 */
    @TableField("follower_count")
    private Integer followerCount;
    /** 关注数 */
    @TableField("following_count")
    private Integer followingCount;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 上次登录时间 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;
}
