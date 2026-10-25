package com.diff.sdk.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 对比配置。实例一经 {@link Builder#build()} 即为不可变对象。
 *
 * <p>{@code ignorePaths} 使用点号路径（数组下标作为普通数字段，如 {@code items.0.name}），
 * 支持 {@code ?} / {@code *} / {@code **} 三种通配符，语义见 {@link PathPatterns}。
 * 命中忽略的路径，其整棵子树都不参与对比。
 */
public final class DiffConfig {

    /** 无限制深度 */
    public static final int UNLIMITED_DEPTH = Integer.MAX_VALUE;

    private static final DiffConfig DEFAULTS = new DiffConfig(
            Collections.<String>emptyList(), UNLIMITED_DEPTH, true);

    /** 忽略路径：精确路径 / 通配符模式，去重且保持声明顺序 */
    private final List<String> ignorePaths;

    /** 最大递归深度，非正数表示不限制 */
    private final int maxDepth;

    /** 数字宽松比较：1 与 1.0 视为相等（跨类型仍判为 MODIFIED） */
    private final boolean lenientNumberComparison;

    private DiffConfig(List<String> ignorePaths, int maxDepth, boolean lenientNumberComparison) {
        this.ignorePaths = Collections.unmodifiableList(new ArrayList<>(ignorePaths));
        this.maxDepth = maxDepth;
        this.lenientNumberComparison = lenientNumberComparison;
    }

    /** 默认配置：不忽略任何路径、不限制深度、开启数字宽松比较（全局共享的不可变实例） */
    public static DiffConfig defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 忽略路径（不可变视图） */
    public List<String> getIgnorePaths() {
        return ignorePaths;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public boolean isLenientNumberComparison() {
        return lenientNumberComparison;
    }

    /**
     * 判断某个点号路径是否被忽略。
     *
     * <p>除路径自身命中模式外，还会向上回溯全部祖先路径：
     * 忽略 {@code user} 等价于忽略 {@code user.name}、{@code user.roles.0} 等整棵子树。
     * 根路径（空串）始终返回 false，保证根节点参与对比。
     */
    public boolean isIgnored(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        List<String> segments = PathPatterns.split(path);
        // 从完整路径开始，逐级去掉最后一段，检查自身及所有祖先
        for (int end = segments.size(); end > 0; end--) {
            String ancestor = String.join(".", segments.subList(0, end));
            if (PathPatterns.matchesAny(ignorePaths, ancestor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiffConfig)) {
            return false;
        }
        DiffConfig that = (DiffConfig) o;
        return maxDepth == that.maxDepth
                && lenientNumberComparison == that.lenientNumberComparison
                && ignorePaths.equals(that.ignorePaths);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ignorePaths, maxDepth, lenientNumberComparison);
    }

    @Override
    public String toString() {
        return "DiffConfig{ignorePaths=" + ignorePaths
                + ", maxDepth=" + (maxDepth == UNLIMITED_DEPTH ? "unlimited" : maxDepth)
                + ", lenientNumberComparison=" + lenientNumberComparison + '}';
    }

    public static final class Builder {
        private final Set<String> ignorePaths = new LinkedHashSet<>();
        private int maxDepth = UNLIMITED_DEPTH;
        private boolean lenientNumberComparison = true;

        private Builder() {
        }

        /** 追加忽略路径（可变参数版），null 或空白元素抛 {@link IllegalArgumentException} */
        public Builder ignorePaths(String... paths) {
            if (paths == null) {
                throw new IllegalArgumentException("ignorePaths must not be null");
            }
            for (String path : paths) {
                addIgnorePath(path);
            }
            return this;
        }

        /** 追加忽略路径（集合版），null 集合抛 {@link NullPointerException} */
        public Builder ignorePaths(Collection<String> paths) {
            Objects.requireNonNull(paths, "ignorePaths");
            for (String path : paths) {
                addIgnorePath(path);
            }
            return this;
        }

        public Builder addIgnorePath(String path) {
            if (path == null) {
                throw new IllegalArgumentException("ignorePath must not be null");
            }
            String trimmed = path.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("ignorePath must not be blank");
            }
            this.ignorePaths.add(trimmed);
            return this;
        }

        /** 设置最大递归深度，非正数表示不限制 */
        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth <= 0 ? UNLIMITED_DEPTH : maxDepth;
            return this;
        }

        /** 是否开启数字宽松比较（默认开启） */
        public Builder lenientNumbers(boolean lenient) {
            this.lenientNumberComparison = lenient;
            return this;
        }

        public DiffConfig build() {
            return new DiffConfig(new ArrayList<>(ignorePaths), maxDepth, lenientNumberComparison);
        }
    }
}
