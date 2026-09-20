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

    public static final Integer MESSAGE_STATUS_UNREAD = 0;
    public static final Integer MESSAGE_STATUS_READ = 1;

    public static final Integer USER_STATUS_NORMAL = 1;
    public static final Integer USER_STATUS_BAN = 0;
    public static final Integer USER_STATUS_DELETED = 2;

    public static final Integer REVIEW_STATUS_DELETE = 0;
    public static final Integer REVIEW_STATUS_NORMAL = 1;
    public static final Integer REVIEW_STATUS_BAN = 2;

    public static final Integer COMMENT_STATUS_NORMAL = 1;
    public static final Integer COMMENT_STATUS_BAN = 2;

    public static final Integer MOVIE_STATUS_NORMAL = 1;
    public static final Integer MOVIE_STATUS_BAN = 0;

    public static final Integer VIPPRODUCT_STATUS_NORMAL = 1;
    public static final Integer VIP_PAY_METHOD_ALIBABA = 1;
    public static final Integer VIP_PAY_METHOD_WECHAT = 2;

    public static final long ORDER_EXPIRE_TIME = 30;
    public static final Integer ORDER_STATUS_PAYING = 0;
    public static final Integer ORDER_STATUS_SUCCESS = 1;
    public static final Integer ORDER_STATUS_CLOSE = 2;
    public static final Integer ORDER_STATUS_REFUND = 3;
    /** 正在向支付渠道创建支付单 */
    public static final Integer ORDER_STATUS_INITIATING = 4;
    /** 支付渠道订单已创建，等待用户支付 */
    public static final Integer ORDER_STATUS_WAIT_PAY = 5;
    /** 正在向支付渠道关单 */
    public static final Integer ORDER_STATUS_CLOSING = 6;
    /** 已扣款但本地无法履约，正在进行幂等退款补偿 */
    public static final Integer ORDER_STATUS_REFUNDING = 7;

}
