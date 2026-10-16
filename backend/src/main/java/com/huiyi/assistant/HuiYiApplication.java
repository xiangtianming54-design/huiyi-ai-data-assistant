package com.huiyi.assistant;

import com.huiyi.assistant.config.AuthProperties;
import com.huiyi.assistant.observability.ObservabilityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * HuiYi AI Data Assistant 主服务启动类。
 *
 * @author huiyi
 */
@MapperScan("com.huiyi.assistant.mapper")
@EnableConfigurationProperties({AuthProperties.class, ObservabilityProperties.class})
@SpringBootApplication
public class HuiYiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuiYiApplication.class, args);
    }
}
