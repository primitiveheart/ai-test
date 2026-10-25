package com.diff.sdk.model;

/**
 * 单条差异的类型。
 *
 * <p>判定规则与前端 json-diff-toolkit 一致：
 * <ul>
 *   <li>oldValue 为空、newValue 非空 → {@link #ADDED}</li>
 *   <li>oldValue 非空、newValue 为空 → {@link #DELETED}</li>
 *   <li>两者都非空 → {@link #MODIFIED}</li>
 * </ul>
 */
public enum DiffType {
    ADDED,
    DELETED,
    MODIFIED
}
