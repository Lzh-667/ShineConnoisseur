package com.lzh.common;

/**
 * 业务异常，用于 Service 层非预期错误（如 DB 更新失败），抛出的消息会直接返回给客户端。
 * 继承 RuntimeException 以确保 @Transactional 事务回滚。
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
