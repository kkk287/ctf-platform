package com.example.ctf.domain;

/** AWD 轮次状态机：PREP → RUNNING ⇄ PAUSED → SCORING → FINISHED。 */
public enum AwdStatus {
    PREP("准备中"),
    RUNNING("进行中"),
    PAUSED("已暂停"),
    SCORING("结算中"),
    FINISHED("已结束");

    private final String label;

    AwdStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
