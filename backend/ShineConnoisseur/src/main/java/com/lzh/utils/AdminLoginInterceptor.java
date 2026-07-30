package com.lzh.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminLoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        //1.判断是否要拦截（ThreadLocal中是否有用户）
        if(AdminHolder.getAdmin()==null){
            response.setStatus(401);
            return false;
        }
        //2.有用户，放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex){
        AdminHolder.removeAdmin();
    }
}
