package com.diff.server.config;

import com.diff.server.model.DiffRequest;
import com.diff.sdk.model.DiffConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把请求的对比参数翻译成 {@link DiffConfig}。
 *
 * <p>请求里未显式指定的字段回落到 {@link DiffProperties} 的默认值，
 * 这样「全局默认忽略 traceId」这类诉求不用改代码，改配置即可。
 */
@Component
public class DiffConfigFactory {

    private final DiffProperties properties;

    public DiffConfigFactory(DiffProperties properties) {
        this.properties = properties;
    }

    /** 构造请求对应的配置 */
    public DiffConfig create(DiffRequest request) {
        DiffConfig.Builder builder = DiffConfig.builder()
                .maxDepth(request.getMaxDepth() != null
                        ? request.getMaxDepth()
                        : properties.getDefaultMaxDepth())
                .lenientNumbers(request.getLenientNumbers() != null
                        ? request.getLenientNumbers()
                        : properties.isLenientNumbers());

        List<String> ignorePaths = request.getIgnorePaths();
        if (ignorePaths != null && !ignorePaths.isEmpty()) {
            builder.ignorePaths(ignorePaths);
        } else if (!properties.getDefaultIgnorePaths().isEmpty()) {
            builder.ignorePaths(properties.getDefaultIgnorePaths());
        }
        return builder.build();
    }

    /** 配置文件定义的默认配置 */
    public DiffConfig defaults() {
        DiffConfig.Builder builder = DiffConfig.builder()
                .maxDepth(properties.getDefaultMaxDepth())
                .lenientNumbers(properties.isLenientNumbers());
        if (!properties.getDefaultIgnorePaths().isEmpty()) {
            builder.ignorePaths(properties.getDefaultIgnorePaths());
        }
        return builder.build();
    }
}
