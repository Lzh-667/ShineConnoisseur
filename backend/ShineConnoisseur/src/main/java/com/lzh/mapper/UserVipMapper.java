package com.lzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzh.po.UserVip;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface UserVipMapper extends BaseMapper<UserVip> {

    /**
     * 依靠 user_id 唯一键原子地开通或续期，避免并发回调互相覆盖到期时间。
     */
    @Insert("""
            INSERT INTO user_vip (user_id, start_time, expire_time)
            VALUES (#{userId}, #{now}, TIMESTAMPADD(DAY, #{durationDays}, #{now}))
            ON DUPLICATE KEY UPDATE
              start_time = IF(expire_time < #{now}, #{now}, start_time),
              expire_time = IF(
                  expire_time < #{now},
                  TIMESTAMPADD(DAY, #{durationDays}, #{now}),
                  TIMESTAMPADD(DAY, #{durationDays}, expire_time)
              )
            """)
    int grantOrExtend(@Param("userId") Long userId,
                      @Param("durationDays") Integer durationDays,
                      @Param("now") LocalDateTime now);
}
