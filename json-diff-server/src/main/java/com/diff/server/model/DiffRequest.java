package com.diff.server.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 对比请求体，字段名与前端 json-diff-view 保持一致。
 *
 * <pre>{@code
 * {
 *   "oldResult": "{\"name\":\"Tom\"}",
 *   "newResult": "{\"name\":\"Jerry\"}",
 *   "ignorePaths": ["meta.**"],
 *   "maxDepth": 20,
 *   "lenientNumbers": true
 * }
 * }</pre>
 *
 * <p>{@code ignorePaths} / {@code maxDepth} / {@code lenientNumbers} 均可省略，
 * 省略时使用服务端的默认配置。
 */
public class DiffRequest {

    /** 旧值 JSON 文本 */
    private String oldResult;

    /** 新值 JSON 文本 */
    private String newResult;

    /** 需要忽略的路径模式，支持 {@code ?} / {@code *} / {@code **}；省略时用服务端默认配置 */
    private List<String> ignorePaths;

    /** 最大递归深度，非正数表示不限制 */
    private Integer maxDepth;

    /** 是否开启数字宽松比较 */
    private Boolean lenientNumbers;

    public String getOldResult() {
        return oldResult;
    }

    public void setOldResult(String oldResult) {
        this.oldResult = oldResult;
    }

    public String getNewResult() {
        return newResult;
    }

    public void setNewResult(String newResult) {
        this.newResult = newResult;
    }

    public List<String> getIgnorePaths() {
        return ignorePaths;
    }

    public void setIgnorePaths(List<String> ignorePaths) {
        this.ignorePaths = ignorePaths == null ? new ArrayList<>() : ignorePaths;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Boolean getLenientNumbers() {
        return lenientNumbers;
    }

    public void setLenientNumbers(Boolean lenientNumbers) {
        this.lenientNumbers = lenientNumbers;
    }
}
