package com.diff.sdk.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 路径模式匹配工具：统一「精确路径 + 通配符」的匹配语义。
 *
 * <p>路径以 {@code .} 分隔（数组下标作为普通数字段，如 {@code items.0.name}）。支持的通配符：
 * <ul>
 *   <li>{@code ?} —— 匹配单个字符，不跨 {@code .}</li>
 *   <li>{@code *} —— 匹配零个或多个字符，不跨 {@code .}</li>
 *   <li>{@code **} —— 匹配零个或多个完整路径段，可跨 {@code .}</li>
 * </ul>
 *
 * <pre>{@code
 * PathPatterns.matches("a.b",        "a.b");       // true
 * PathPatterns.matches("a.*.c",      "a.b.c");     // true
 * PathPatterns.matches("a.**.name",  "a.0.x.name");// true
 * PathPatterns.matches("**",         "a.b.c");     // true
 * }</pre>
 *
 * <p>注意：模式 {@code a.b} 只匹配路径 {@code a.b} 本身，不匹配 {@code a.b.c}；
 * 子树级别的忽略由 {@link DiffConfig#isIgnored(String)} 额外向上回溯祖先路径实现。
 */
public final class PathPatterns {

    private PathPatterns() {
    }

    /**
     * 判断路径是否匹配某个模式。
     *
     * @param pattern 模式串，null 时返回 false
     * @param path    点号路径，null 时返回 false
     * @return 是否匹配
     */
    public static boolean matches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        return matchSegments(split(pattern), split(path), 0, 0);
    }

    /**
     * 判断路径是否命中任一模式。
     *
     * @param patterns 模式集合，null 或空时返回 false
     * @param path     点号路径
     * @return 是否命中
     */
    public static boolean matchesAny(Collection<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty() || path == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** 按 {@code .} 切分路径，忽略空段 */
    public static List<String> split(String path) {
        List<String> segments = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return segments;
        }
        for (String segment : path.split("\\.", -1)) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private static boolean matchSegments(List<String> patterns, List<String> paths, int pi, int ti) {
        if (pi == patterns.size()) {
            return ti == paths.size();
        }
        String pattern = patterns.get(pi);
        if ("**".equals(pattern)) {
            // ** 可以吞掉 0..n 个完整段
            for (int consumed = ti; consumed <= paths.size(); consumed++) {
                if (matchSegments(patterns, paths, pi + 1, consumed)) {
                    return true;
                }
            }
            return false;
        }
        if (ti >= paths.size()) {
            return false;
        }
        if (matchSegment(pattern, paths.get(ti))) {
            return matchSegments(patterns, paths, pi + 1, ti + 1);
        }
        return false;
    }

    /** 单段内的 {@code ?} / {@code *} 通配匹配（经典双指针回溯算法） */
    private static boolean matchSegment(String pattern, String segment) {
        int pi = 0;
        int si = 0;
        int star = -1;
        int mark = 0;
        while (si < segment.length()) {
            if (pi < pattern.length()) {
                char pc = pattern.charAt(pi);
                if (pc == '?' || pc == segment.charAt(si)) {
                    pi++;
                    si++;
                    continue;
                }
                if (pc == '*') {
                    star = pi++;
                    mark = si;
                    continue;
                }
            }
            if (star >= 0) {
                pi = star + 1;
                si = ++mark;
                continue;
            }
            return false;
        }
        while (pi < pattern.length() && pattern.charAt(pi) == '*') {
            pi++;
        }
        return pi == pattern.length();
    }
}
