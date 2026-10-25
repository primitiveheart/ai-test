package com.diff.server.model;

import com.diff.sdk.model.DiffItem;
import com.diff.sdk.model.DiffResult;
import com.diff.sdk.model.DiffValues;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一响应信封。成功时 {@code status=ok}，失败时 {@code status=error} 且带上
 * {@code exceptionMsg}；{@code diffResult} 始终存在，前端无需做空判断。
 */
public class DiffResponse {

    private static final String STATUS_OK = "ok";
    private static final String STATUS_ERROR = "error";

    private String status;

    private String exceptionMsg;

    private DiffSummary summary;

    private List<DiffRow> diffResult = new ArrayList<>();

    public DiffResponse() {
    }

    /** 由 SDK 的对比结果构造成功响应 */
    public static DiffResponse ok(DiffResult result) {
        DiffResponse response = new DiffResponse();
        response.status = STATUS_OK;
        response.exceptionMsg = "";

        List<DiffItem> items = result.getItems();
        response.summary = new DiffSummary(
                result.getAddedCount(),
                result.getDeletedCount(),
                result.getModifiedCount(),
                items.size());

        List<DiffRow> rows = new ArrayList<>(items.size());
        for (DiffItem item : items) {
            rows.add(new DiffRow(item.getPath(),
                    DiffValues.toText(item.getOldValue()),
                    DiffValues.toText(item.getNewValue())));
        }
        response.diffResult = rows;
        return response;
    }

    /** 构造失败响应 */
    public static DiffResponse error(String message) {
        DiffResponse response = new DiffResponse();
        response.status = STATUS_ERROR;
        response.exceptionMsg = message == null ? "" : message;
        response.summary = new DiffSummary(0, 0, 0, 0);
        response.diffResult = Collections.emptyList();
        return response;
    }

    /** 供业务代码判断成功与否；不参与序列化（{@code status} 已经是契约字段） */
    @JsonIgnore
    public boolean isOk() {
        return STATUS_OK.equals(status);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExceptionMsg() {
        return exceptionMsg;
    }

    public void setExceptionMsg(String exceptionMsg) {
        this.exceptionMsg = exceptionMsg;
    }

    public DiffSummary getSummary() {
        return summary;
    }

    public void setSummary(DiffSummary summary) {
        this.summary = summary;
    }

    public List<DiffRow> getDiffResult() {
        return diffResult;
    }

    public void setDiffResult(List<DiffRow> diffResult) {
        this.diffResult = diffResult;
    }
}
