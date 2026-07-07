package com.lzh.po;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.annotation.security.DenyAll;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("admin")
public class Admin {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录密码 */
    private String password;

    /** 管理员姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 头像 */
    private String avatar;

    /** 状态：0-禁用 1-正常 */
    private Integer status;

    /** 权限等级：
     * 0-普通管理员
     * 1-超级管理员
     */
    private Integer role;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
