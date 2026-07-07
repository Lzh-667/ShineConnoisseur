package com.lzh.utils;


import com.lzh.dto.AdminDTO;

public class AdminHolder {
    private static final ThreadLocal<AdminDTO> tl = new ThreadLocal<>();

    /**
     * 保存当前登录用户信息到ThreadLocal
     * @param user 用户id
     */
    public static void setAdmin(AdminDTO user) {
        tl.set(user);
    }

    /**
     * 获取当前登录用户信息
     * @return 用户id
     */
    public static AdminDTO getAdmin() {
        return tl.get();
    }

    /**
     * 移除当前登录用户信息
     */
    public static void removeAdmin(){
        tl.remove();
    }
}
