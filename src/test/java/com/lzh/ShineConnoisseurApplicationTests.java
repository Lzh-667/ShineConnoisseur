package com.lzh;

import com.lzh.po.User;
import com.lzh.service.IUserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@SpringBootTest
class ShineConnoisseurApplicationTests {

    @Resource
    private IUserService userService;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Test
    public void encodePassword(){
        List<User> users = userService.list();
        for(User user : users){
            String oldPassword = user.getPassword();
            //避免重复加密
            if(oldPassword.startsWith("$2a$")){
                continue;
            }
            user.setPassword(
                    passwordEncoder.encode(oldPassword)
            );
            userService.updateById(user);
        }
    }
}
