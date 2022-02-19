package cn.zack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        /**
         * 初始化spring
         * 当需要屏幕预览等操作时, 设置headless为false, 否则会报java.awt.HeadlessException
         * java.awt.headless是J2SE的一种模式, 用于在缺失显示屏、鼠标或者键盘时的系统配置, springboot默认将这个属性设置为true
         */
        new SpringApplicationBuilder(Application.class).headless(false).run(args);
//        SpringApplication.run(Application.class, args);
    }
}
