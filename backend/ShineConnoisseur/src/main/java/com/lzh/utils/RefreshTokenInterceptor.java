package com.lzh.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.lzh.dto.UserDTO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final UserDTO GUEST = new UserDTO();
    static { GUEST.setId(0L); }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            setGuestIfPublicRead(request);
            return true;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            setGuestIfPublicRead(request);
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.setUser(userDTO);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    private void setGuestIfPublicRead(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod()) && isPublicRead(request.getServletPath())) {
            UserHolder.setUser(GUEST);
        }
    }

    private boolean isPublicRead(String path) {
        if (path.startsWith("/movies/") && !path.startsWith("/movies/favorite") && !path.startsWith("/movies/or/")) {
            return true;
        }
        if (path.startsWith("/reviews/") && !path.equals("/reviews/my")) {
            return true;
        }
        if (path.startsWith("/reviewComments/") && !path.equals("/reviewComments/my")
                && !path.startsWith("/reviewComments/like/") && !path.startsWith("/reviewComments/publish/")) {
            return true;
        }
        if (path.startsWith("/users/info/")) {
            return true;
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
