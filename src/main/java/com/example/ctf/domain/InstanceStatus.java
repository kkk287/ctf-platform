package com.example.ctf.domain;

/** 靶机实例状态机。 */
public enum InstanceStatus {
    /** 已创建，等待 orchestrator 拉起 */
    PENDING,
    /** 容器已启动且健康检查通过，对外可访问 */
    RUNNING,
    /** 用户主动销毁 */
    STOPPED,
    /** TTL 到期被调度器回收 */
    EXPIRED,
    /** 拉起或运行过程中失败 */
    FAILED
}
