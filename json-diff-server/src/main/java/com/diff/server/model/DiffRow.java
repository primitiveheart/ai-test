package com.diff.server.model;

/**
 * 一行差异记录，对应前端结果表格的一行。
 *
 * <p>{@code oldValue} / {@code newValue} 一律是字符串：容器值是紧凑 JSON 文本，
 * 缺失侧是 {@code null}。这与页面 {@code obj2Str} 处理后的数据完全一致。
 */
public class DiffRow {

    /** 差异路径，点号分隔，数组下标作为普通数字段（如 {@code items.0.name}） */
    private String key;

    /** 旧值文本，新增时为 null */
    private String oldValue;

    /** 新值文本，删除时为 null */
    private String newValue;

    public DiffRow() {
    }

    public DiffRow(String key, String oldValue, String newValue) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
}
