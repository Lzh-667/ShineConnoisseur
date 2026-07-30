package com.lzh.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.lzh.dto.AdminDTO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshAdminTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        //2.基于token获取用户
        String key=RedisConstants.ADMIN_LOGIN_KEY+token;
        Map<Object, Object> adminMap = stringRedisTemplate.opsForHash().entries(key);
        //3.判断用户是否存在
        if(adminMap.isEmpty()){
            return true;
        }
        //4.将hash值数据转为AdminDTO
        AdminDTO adminDTO = BeanUtil.fillBeanWithMap(adminMap, new AdminDTO(), false);
        //5.存在，保存到ThreadLocal
        AdminHolder.setAdmin(adminDTO);
        //6.刷新token有效期
        stringRedisTemplate.expire(key,RedisConstants.ADMIN_LOGIN_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex){
        AdminHolder.removeAdmin();
    }
}
