package com.lzh.service;

/** 在数据库事务提交后使缓存失效。 */
public interface CacheAfterCommitService {
    void evict(String operation, String... keys);
}
