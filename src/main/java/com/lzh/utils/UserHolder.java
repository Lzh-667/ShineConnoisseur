package com.lzh.utils;

import com.lzh.dto.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    /**
     * 保存当前登录用户信息到ThreadLocal
     * @param user 用户id
     */
    public static void setUser(UserDTO user) {
        tl.set(user);
    }

    /**
     * 获取当前登录用户信息
     * @return 用户id
     */
    public static UserDTO getUser() {
        return tl.get();
    }

    /**
     * 移除当前登录用户信息
     */
    public static void removeUser(){
        tl.remove();
    }
}
