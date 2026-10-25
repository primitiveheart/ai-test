package com.diff.sdk;

import com.diff.sdk.model.DiffConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link JsonDiffer} 工厂。
 *
 * <pre>{@code
 * JsonDiffer differ = JsonDiffers.create();                       // 默认配置
 * JsonDiffer custom = JsonDiffers.create(DiffConfig.builder()
 *         .ignorePaths("meta.**")
 *         .maxDepth(10)
 *         .build());
 * }</pre>
 *
 * <p>工厂方法每次返回新实例；实例无状态、线程安全，可复用或共享。
 */
public final class JsonDiffers {

    private JsonDiffers() {
    }

    /** 使用默认配置创建对比器 */
    public static JsonDiffer create() {
        return create(DiffConfig.defaults());
    }

    /** 使用指定配置创建对比器，config 为 null 时回落到默认配置 */
    public static JsonDiffer create(DiffConfig config) {
        return create(config, new ObjectMapper());
    }

    /**
     * 使用指定配置与 ObjectMapper 创建对比器。
     *
     * <p>传入自定义 {@code mapper} 可控制解析行为（如允许注释、单引号、大数字精度等）。
     *
     * @param config 对比配置，null 时使用默认配置
     * @param mapper 解析器，null 时使用新建的 {@link ObjectMapper}
     * @return 对比器实例
     */
    public static JsonDiffer create(DiffConfig config, ObjectMapper mapper) {
        ObjectMapper effectiveMapper = mapper == null ? new ObjectMapper() : mapper;
        return new DefaultJsonDiffer(effectiveMapper, config == null ? DiffConfig.defaults() : config);
    }
}
