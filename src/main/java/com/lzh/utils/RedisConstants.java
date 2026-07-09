package com.lzh.utils;

public class RedisConstants {
    /**登录验证码*/
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    /**登录成功存token*/
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;
    /**用户验证码登录错误次数*/
    public static final String LOGIN_USER_CODE_ERR_KEY = "login:user:code:err:";
    public static final Long LOGIN_USER_CODE_ERR_TTL = 10L;
    /**用户密码登录错误次数*/
    public static final String LOGIN_USER_PASSWORD_ERR_KEY = "login:user:password:err:";
    public static final Long LOGIN_USER_PASSWORD_ERR_TTL = 10L;
    /**用户注册发送验证码*/
    public static final String REGISTER_CODE_KEY = "register:code:";
    public static final Long REGISTER_CODE_TTL = 5L;
    /**用户注册错误次数*/
    public static final String REGISTER_USER_ERR_KEY = "register:user:err:";
    public static final Long REGISTER_USER_ERR_TTL = 10L;
    /**管理员登录成功存token*/
    public static final String ADMIN_LOGIN_KEY = "admin:token:";
    public static final Long ADMIN_LOGIN_TTL = 30L;
    /**管理员登录错误次数*/
    public static final String LOGIN_ADMIN_PASSWORD_ERR_KEY = "login:admin:password:err:";
    public static final Long LOGIN_ADMIN_PASSWORD_ERR_TTL = 10L;
    /**关注*/
    public static final String FOLLOWING_KEY = "followings:";
    public static final Long FOLLOWING_TTL = 60L;
    public static final Long FOLLOWING_EMPTY_TTL = 60L;
    /**粉丝*/
    public static final String FOLLOWER_KEY = "followers:";
    public static final Long FOLLOWER_TTL = 60L;
    public static final Long FOLLOWER_EMPTY_TTL = 60L;
    /**影评*/
    public static final String LIKE_REVIEW_KEY = "review:like:";
    /**电影*/
    public static final String MOVIE_INFO_KEY = "movie:info:";
    public static final Long MOVIE_INFO_TTL = 5L;
}
