package com.diff.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 跨域配置：允许 json-diff-view 开发服务器直连接口。
 *
 * <p>白名单来自 {@link DiffProperties}，因此在这里一并启用配置绑定 ——
 * 放在 {@code @ConfigurationPropertiesScan} 下 {@code @WebMvcTest} 切片不会加载它，
 * 会导致 Web 层测试起不来。
 */
@Configuration
@EnableConfigurationProperties(DiffProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final DiffProperties properties;

    public WebConfig(DiffProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = properties.getCors().getAllowedOrigins();
        String[] allowedOrigins = origins.toArray(new String[0]);

        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
