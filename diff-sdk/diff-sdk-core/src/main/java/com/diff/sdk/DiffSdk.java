package com.diff.sdk;

import com.diff.sdk.model.DiffConfig;
import com.diff.sdk.model.DiffResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * diff-sdk 的门面（Facade）：最常用的对比入口。
 *
 * <p>实例不可变且线程安全，建议在应用中作为单例复用：
 *
 * <pre>{@code
 * DiffSdk sdk = new DiffSdk(DiffConfig.builder()
 *         .ignorePaths("meta.**", "*.timestamp")
 *         .maxDepth(20)
 *         .build());
 *
 * DiffResult result = sdk.compare(oldJson, newJson);
 * System.out.println(result.toJson());
 * }</pre>
 *
 * <p>一次性调用可直接使用静态方法 {@link #compareJson(String, String, DiffConfig)}。
 */
public final class DiffSdk {

    private final DiffConfig config;
    private final ObjectMapper mapper;
    private final JsonDiffer differ;

    /** 使用默认配置 */
    public DiffSdk() {
        this(DiffConfig.defaults());
    }

    /** 使用指定配置，config 为 null 时回落到默认配置 */
    public DiffSdk(DiffConfig config) {
        this(config, null);
    }

    /**
     * @param config 对比配置，null 时使用默认配置
     * @param mapper 解析器，null 时使用新建的 {@link ObjectMapper}
     */
    public DiffSdk(DiffConfig config, ObjectMapper mapper) {
        this.config = config == null ? DiffConfig.defaults() : config;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        this.differ = JsonDiffers.create(this.config, this.mapper);
    }

    /** 使用默认配置创建实例 */
    public static DiffSdk create() {
        return new DiffSdk();
    }

    /** 使用指定配置创建实例 */
    public static DiffSdk create(DiffConfig config) {
        return new DiffSdk(config);
    }

    /**
     * 对比两份 JSON 字符串。
     *
     * @param oldValue 旧值 JSON，null 或空白视为「不存在」
     * @param newValue 新值 JSON，null 或空白视为「不存在」
     * @return 差异结果
     * @throws IllegalArgumentException 入参不是合法 JSON
     */
    public DiffResult compare(String oldValue, String newValue) {
        return differ.diffJson(oldValue, newValue, config);
    }

    /** 对比两棵已解析的 JSON 树，避免重复解析 */
    public DiffResult compare(JsonNode oldValue, JsonNode newValue) {
        return differ.diff(oldValue, newValue, config);
    }

    /** 对比两个任意 Java 对象（POJO / Map / List），先转成 JSON 树再对比 */
    public DiffResult compareObjects(Object oldValue, Object newValue) {
        return compare(mapper.valueToTree(oldValue), mapper.valueToTree(newValue));
    }

    /** 当前实例使用的对比配置 */
    public DiffConfig getConfig() {
        return config;
    }

    /** 使用默认配置一次性对比两份 JSON 字符串 */
    public static DiffResult compareJson(String oldValue, String newValue) {
        return compareJson(oldValue, newValue, DiffConfig.defaults());
    }

    /** 使用指定配置一次性对比两份 JSON 字符串 */
    public static DiffResult compareJson(String oldValue, String newValue, DiffConfig config) {
        Objects.requireNonNull(config, "config");
        return JsonDiffers.create(config).diffJson(oldValue, newValue, config);
    }
}
