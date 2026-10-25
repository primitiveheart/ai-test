package com.diff.sdk.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 一次 JSON 对比的结果，包含全部差异项（不可变）。
 *
 * <p>除差异项外，还可携带「按 {@link DiffConfig#getIgnorePaths()} 清洗后」的两份 JSON
 * （{@link #getOldJson()} / {@link #getNewJson()}），便于前端直接渲染高亮视图；
 * 未做清洗时二者为 {@code null}。
 */
public final class DiffResult {

    private final List<DiffItem> items;

    /** 清洗后的旧值，未清洗时为 null */
    private final JsonNode oldJson;

    /** 清洗后的新值，未清洗时为 null */
    private final JsonNode newJson;

    public DiffResult(List<DiffItem> items) {
        this(items, null, null);
    }

    public DiffResult(List<DiffItem> items, JsonNode oldJson, JsonNode newJson) {
        List<DiffItem> copy = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(items, "items")));
        this.items = copy;
        this.oldJson = oldJson;
        this.newJson = newJson;
    }

    /** 无差异的结果 */
    public static DiffResult empty() {
        return new DiffResult(Collections.<DiffItem>emptyList());
    }

    public List<DiffItem> getItems() {
        return items;
    }

    /** 按类型筛选差异项 */
    public List<DiffItem> getItems(DiffType type) {
        List<DiffItem> filtered = new ArrayList<>();
        for (DiffItem item : items) {
            if (item.getType() == type) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    /** 全部差异路径，顺序与 items 一致 */
    public List<String> getPaths() {
        List<String> paths = new ArrayList<>(items.size());
        for (DiffItem item : items) {
            paths.add(item.getPath());
        }
        return paths;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int getAddedCount() {
        return count(DiffType.ADDED);
    }

    public int getDeletedCount() {
        return count(DiffType.DELETED);
    }

    public int getModifiedCount() {
        return count(DiffType.MODIFIED);
    }

    public JsonNode getOldJson() {
        return oldJson;
    }

    public JsonNode getNewJson() {
        return newJson;
    }

    /**
     * 转成可直接返回给前端的结构：{@code {summary:{added,deleted,modified,total}, items:[...]}}。
     *
     * <p>{@code oldValue} / {@code newValue} 为原始 JSON 值（对象/数组原样保留为 Map/List），
     * {@code type} 为小写类型名，与前端 {@code added/deleted/modified} 对齐。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("added", getAddedCount());
        summary.put("deleted", getDeletedCount());
        summary.put("modified", getModifiedCount());
        summary.put("total", items.size());

        List<Map<String, Object>> itemMaps = new ArrayList<>(items.size());
        for (DiffItem item : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", item.getPath());
            map.put("type", item.getType().name().toLowerCase(Locale.ROOT));
            map.put("oldValue", scalar(item.getOldValue()));
            map.put("newValue", scalar(item.getNewValue()));
            itemMaps.add(map);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summary", summary);
        root.put("items", itemMaps);
        return root;
    }

    /** 序列化为 JSON 字符串，结构同 {@link #toMap()} */
    public String toJson() {
        return DiffValues.writeJson(toMap());
    }

    /**
     * 转成前端 json-diff-view 约定的响应结构：
     * {@code {status:"ok", summary:{added,deleted,modified,total}, diffResult:[{key,oldValue,newValue}]}}。
     *
     * <p>与 {@link #toMap()} 的区别：这里用 {@code key} 指代路径，且 oldValue / newValue
     * 一律序列化为<strong>字符串</strong>（对象/数组用 {@code JSON.stringify} 等价形式，
     * 缺失值用 JSON {@code null}），与页面 {@code obj2Str} 处理后的数据完全一致。
     */
    public Map<String, Object> toViewMap() {
        Map<String, Object> summary = summaryMap();

        List<Map<String, Object>> diffResult = new ArrayList<>(items.size());
        for (DiffItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", item.getPath());
            row.put("oldValue", text(item.getOldValue()));
            row.put("newValue", text(item.getNewValue()));
            diffResult.add(row);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("status", "ok");
        root.put("summary", summary);
        root.put("diffResult", diffResult);
        return root;
    }

    /** 序列化为 JSON 字符串，结构同 {@link #toViewMap()} */
    public String toViewJson() {
        return DiffValues.writeJson(toViewMap());
    }

    private Map<String, Object> summaryMap() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("added", getAddedCount());
        summary.put("deleted", getDeletedCount());
        summary.put("modified", getModifiedCount());
        summary.put("total", items.size());
        return summary;
    }

    /** JsonNode 转普通 Java 值，便于 Jackson 序列化；NullNode 统一为 null */
    private static Object scalar(Object value) {
        return DiffValues.isAbsent(value) ? null : value;
    }

    /** 把差异值转成前端展示用的字符串，规则见 {@link DiffValues#toText(Object)} */
    private static String text(Object value) {
        return DiffValues.toText(value);
    }

    private int count(DiffType type) {
        int count = 0;
        for (DiffItem item : items) {
            if (item.getType() == type) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiffResult)) {
            return false;
        }
        DiffResult that = (DiffResult) o;
        return items.equals(that.items)
                && Objects.equals(oldJson, that.oldJson)
                && Objects.equals(newJson, that.newJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, oldJson, newJson);
    }

    @Override
    public String toString() {
        return "DiffResult{added=" + getAddedCount()
                + ", deleted=" + getDeletedCount()
                + ", modified=" + getModifiedCount()
                + ", items=" + items + '}';
    }
}
