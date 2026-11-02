package com.example.ctf.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。HTTP 状态与业务码分离，便于前端精确提示。
 */
public enum ErrorCode {
    INVALID_PARAM(4002, "参数不合法", HttpStatus.BAD_REQUEST),
    INVALID_STATE(4001, "当前状态不允许该操作", HttpStatus.CONFLICT),
    WRONG_FLAG(4004, "flag 不正确", HttpStatus.BAD_REQUEST),
    ALREADY_SOLVED(4009, "该题目已解出", HttpStatus.CONFLICT),
    DUPLICATE(4090, "数据冲突", HttpStatus.CONFLICT),
    NOT_FOUND(4040, "资源不存在", HttpStatus.NOT_FOUND),
    UNAUTHORIZED(4010, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(4011, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(4030, "无权访问该资源", HttpStatus.FORBIDDEN),
    EVENT_NOT_STARTED(4031, "赛事尚未开始，题目暂不可见", HttpStatus.FORBIDDEN),
    EVENT_ENDED(4032, "赛事已结束，无法继续提交", HttpStatus.FORBIDDEN),
    ACCOUNT_BANNED(4033, "账号已被封禁", HttpStatus.FORBIDDEN),
    RATE_LIMITED(4290, "错误提交次数过多，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    COOLDOWN(4291, "提交过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    FILE_TOO_LARGE(4003, "附件超过大小限制", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(5000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(int code, String defaultMessage, HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }
}
