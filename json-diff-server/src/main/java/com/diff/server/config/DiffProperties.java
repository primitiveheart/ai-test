package com.diff.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 对比服务的可配置项，对应配置文件里的 {@code diff.*} 前缀。
 */
@ConfigurationProperties(prefix = "diff")
public class DiffProperties {

    /** 请求体里单份 JSON 允许的最大字符数，超出直接拒绝，避免超大 payload 打爆内存 */
    private int maxJsonLength = 5 * 1024 * 1024;

    /** 请求未显式指定 ignorePaths 时使用的默认忽略路径 */
    private List<String> defaultIgnorePaths = new ArrayList<>();

    /** 请求未显式指定 maxDepth 时使用的默认递归深度，非正数表示不限制 */
    private int defaultMaxDepth = 0;

    /** 请求未显式指定 lenientNumbers 时是否开启数字宽松比较 */
    private boolean lenientNumbers = true;

    private final Cors cors = new Cors();

    public int getMaxJsonLength() {
        return maxJsonLength;
    }

    public void setMaxJsonLength(int maxJsonLength) {
        this.maxJsonLength = maxJsonLength;
    }

    public List<String> getDefaultIgnorePaths() {
        return defaultIgnorePaths;
    }

    public void setDefaultIgnorePaths(List<String> defaultIgnorePaths) {
        this.defaultIgnorePaths = defaultIgnorePaths == null ? new ArrayList<>() : defaultIgnorePaths;
    }

    public int getDefaultMaxDepth() {
        return defaultMaxDepth;
    }

    public void setDefaultMaxDepth(int defaultMaxDepth) {
        this.defaultMaxDepth = defaultMaxDepth;
    }

    public boolean isLenientNumbers() {
        return lenientNumbers;
    }

    public void setLenientNumbers(boolean lenientNumbers) {
        this.lenientNumbers = lenientNumbers;
    }

    public Cors getCors() {
        return cors;
    }

    /** 跨域配置 */
    public static class Cors {

        /** 允许访问的前端来源，开发态默认是 Vite 的 5173 端口 */
        private List<String> allowedOrigins = new ArrayList<>(
                Arrays.asList("http://localhost:5173", "http://127.0.0.1:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : allowedOrigins;
        }
    }
}
