package com.lzh.config;

import com.lzh.utils.AdminLoginInterceptor;
import com.lzh.utils.LoginInterceptor;
import com.lzh.utils.RefreshAdminTokenInterceptor;
import com.lzh.utils.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {
    @Resource
    private LoginInterceptor loginInterceptor;
    @Resource
    private RefreshTokenInterceptor refreshTokenInterceptor;
    @Resource
    private AdminLoginInterceptor adminLoginInterceptor;
    @Resource
    private RefreshAdminTokenInterceptor refreshAdminTokenInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**").excludePathPatterns(
                        "/users/login/code",
                        "/users/login/password",
                        "/users/code",
                        "/users/register",
                        "/users/registerCode",
                        "/admins/**"
                ).order(2);

        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**").excludePathPatterns(
                        "/admins/**"
                ).order(1);

        registry.addInterceptor(refreshAdminTokenInterceptor)
                .addPathPatterns("/admins/**").order(1);

        registry.addInterceptor(adminLoginInterceptor)
                .addPathPatterns("/admins/**").excludePathPatterns(
                        "/admins/login"
                )
                .order(2);
    }
}
