package com.lzh.utils;

public class SystemConstants {
    public static final int MAX_PAGE_SIZE = 10;

    public static final int TARGET_COMMENT = 1;
    public static final int TARGET_REVIEW = 2;

    public static final int MESSAGE_TYPE_FOLLOW = 0;
    public static final int MESSAGE_TYPE_LIKE_REVIEW = 1;
    public static final int MESSAGE_TYPE_COMMENT = 2;
    public static final int MESSAGE_TYPE_LIKE_COMMENT = 3;
    public static final int MESSAGE_TYPE_REPLY_COMMENT = 4;

    public static final int MESSAGE_TARGET_USER = 0;
    public static final int MESSAGE_TARGET_REVIEW = 1;
    public static final int MESSAGE_TARGET_COMMENT = 2;

    public static final Integer USER_STATUS_NORMAL = 1;
    public static final Integer USER_STATUS_BAN = 0;

    public static final Integer REVIEW_STATUS_BAN = 0;
    public static final Integer REVIEW_STATUS_NORMAL = 1;
    public static final Integer REVIEW_STATUS_AUDITING = 2;

    public static final Integer COMMENT_STATUS_BAN = 0;
    public static final Integer COMMENT_STATUS_NORMAL = 1;
}
