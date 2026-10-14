package com.huiyi.assistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HuiYi AI Data Assistant 主服务启动类。
 *
 * @author huiyi
 */
@MapperScan("com.huiyi.assistant.mapper")
@SpringBootApplication
public class HuiYiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuiYiApplication.class, args);
    }
}
