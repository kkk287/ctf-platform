package com.example.ctf.domain;

public enum Role {
    /** 参赛选手 */
    USER,
    /** 裁判：可进入管理后台处理日常运营，但用户封禁/角色变更等敏感操作仅 ADMIN */
    JUDGE,
    /** 教师：练习场运营（预留，后续 P2+ 开放组卷等能力） */
    TEACHER,
    /** 观摩账号：只读，禁止提交/组队等参与性操作 */
    OBSERVER,
    /** 平台管理员 */
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
