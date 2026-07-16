package com.lzh.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.lzh.common.Result;
import com.lzh.service.*;
import com.lzh.utils.RedisConstants;
import com.lzh.vo.AdminDashboardVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AdminDashboardServiceImpl implements IAdminDashboardService {
    @Resource
    private IUserService userService;
    @Resource
    private IReviewService reviewService;
    @Resource
    private IMovieService movieService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void refreshDashboardCache() {
        //1.查询数据
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setUserCount(userService.count());
        vo.setMovieCount(movieService.count());
        vo.setReviewCount(reviewService.count());
        vo.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        LocalDateTime today = LocalDate.now().atStartOfDay();
        vo.setTodayReviewCount(reviewService.query().ge("create_time",today).count());

        LocalDateTime week = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        vo.setWeekReviewCount(reviewService.query().ge("create_time",week).count());

        Map<String,String> map = new HashMap<>();
        map.put("userCount", String.valueOf(vo.getUserCount()));
        map.put("movieCount", String.valueOf(vo.getMovieCount()));
        map.put("reviewCount", String.valueOf(vo.getReviewCount()));
        map.put("todayReviewCount", String.valueOf(vo.getTodayReviewCount()));
        map.put("weekReviewCount", String.valueOf(vo.getWeekReviewCount()));
        map.put("updateTime", String.valueOf(vo.getUpdateTime()));
        //2.写入redis
        String oldKey = RedisConstants.ADMIN_DASHBOARD_KEY;
        String newKey = oldKey + ":temp";
        stringRedisTemplate.delete(newKey);
        stringRedisTemplate.opsForHash()
                .putAll(
                        newKey,
                        map
                );
        stringRedisTemplate.rename(newKey, oldKey);
    }
    @Override
    public Result dashboard() {
        //1.查询redis
        Map<Object,Object> map = stringRedisTemplate.opsForHash().entries(RedisConstants.ADMIN_DASHBOARD_KEY);
        //2.不存在，不符合正常情况，返回异常
        if (map.isEmpty()) {
            refreshDashboardCache();
            map = stringRedisTemplate.opsForHash().entries(RedisConstants.ADMIN_DASHBOARD_KEY);
        }
        //3.返回数据
        AdminDashboardVO vo = BeanUtil.fillBeanWithMap(
                map,
                new AdminDashboardVO(),
                false
        );
        return Result.ok(vo);
    }
}
