package com.lzh.utils;

public class PasswordEncoder {
    public static boolean matches(String password, String password1) {
        return password.equals(password1);
    }

    public static String encode(String password) {
        return password;
    }
}
