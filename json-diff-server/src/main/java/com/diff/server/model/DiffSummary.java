package com.diff.server.model;

/** 差异统计，用于结果页顶部的汇总展示。 */
public class DiffSummary {

    private int added;

    private int deleted;

    private int modified;

    private int total;

    public DiffSummary() {
    }

    public DiffSummary(int added, int deleted, int modified, int total) {
        this.added = added;
        this.deleted = deleted;
        this.modified = modified;
        this.total = total;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public int getModified() {
        return modified;
    }

    public void setModified(int modified) {
        this.modified = modified;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
