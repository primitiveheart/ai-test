package com.diff.sdk.model;

import java.util.Objects;

/**
 * 单条 JSON 差异。
 *
 * <p>path 使用点号路径（如 {@code a.b.2}），与前端 JsonDiffViewer 内部格式一致。
 * oldValue / newValue 为 JSON 值（可为 null）：ADDED 时 oldValue 为 null，DELETED 时 newValue 为 null。
 */
public final class DiffItem {
    private final String path;
    private final DiffType type;
    private final Object oldValue;
    private final Object newValue;

    public DiffItem(String path, DiffType type, Object oldValue, Object newValue) {
        this.path = Objects.requireNonNull(path, "path");
        this.type = Objects.requireNonNull(type, "type");
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getPath() {
        return path;
    }

    public DiffType getType() {
        return type;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiffItem)) {
            return false;
        }
        DiffItem diffItem = (DiffItem) o;
        return path.equals(diffItem.path)
                && type == diffItem.type
                && Objects.equals(oldValue, diffItem.oldValue)
                && Objects.equals(newValue, diffItem.newValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, type, oldValue, newValue);
    }

    @Override
    public String toString() {
        return "DiffItem{path='" + path + "', type=" + type
                + ", oldValue=" + oldValue + ", newValue=" + newValue + '}';
    }
}
