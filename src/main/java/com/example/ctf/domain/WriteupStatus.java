package com.example.ctf.domain;

/** Writeup 状态 */
public enum WriteupStatus {
    /** 草稿：作者可编辑，不可见 */
    DRAFT("草稿"),
    /** 已提交：等待管理员审核；赛后自动开放为可见 */
    SUBMITTED("已提交"),
    /** 已采纳：管理端审核通过，赛后可见并可沉淀入题库 */
    ACCEPTED("已采纳"),
    /** 已驳回：管理端审核不通过 */
    REJECTED("已驳回");

    private final String label;

    WriteupStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
