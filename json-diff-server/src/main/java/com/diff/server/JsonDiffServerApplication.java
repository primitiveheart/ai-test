package com.diff.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JSON 对比服务启动类。
 *
 * <p>默认监听 {@code http://localhost:8080}，对外暴露 {@code /api/json-diff/**} 接口。
 */
@SpringBootApplication
public class JsonDiffServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JsonDiffServerApplication.class, args);
    }
}
