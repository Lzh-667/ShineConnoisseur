package com.lzh;

import com.lzh.po.User;
import com.lzh.service.IUserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static cn.hutool.core.lang.Validator.isUUID;

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
    @Test
    public void renameMovieImages() {
        // 图片目录
        String path = "uploads/images/movie/";

        File dir = new File(path);

        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("目录不存在：" + path);
            return;
        }
        File[] files = dir.listFiles();

        if (files == null || files.length == 0) {
            System.out.println("没有图片文件");
            return;
        }
        for (File file : files) {
            // 跳过文件夹
            if (!file.isFile()) {
                continue;
            }
            String oldName = file.getName();
            // 获取文件名和后缀
            int index = oldName.lastIndexOf(".");
            String name = index > 0 ? oldName.substring(0, index) : oldName;
            String suffix = index > 0 ? oldName.substring(index) : "";
            // 已经是UUID，跳过
            if (isUUID(name)) {
                System.out.println("跳过：" + oldName);
                continue;
            }
            // 生成新的UUID文件名
            String newName = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    + suffix;

            File newFile = new File(dir, newName);
            // 重命名
            boolean success = file.renameTo(newFile);

            if (success) {
                System.out.println(oldName + " -> " + newName);
            } else {
                System.out.println("重命名失败：" + oldName);
            }
        }
        System.out.println("图片重命名完成");
    }
}
