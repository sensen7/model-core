package com.modelcore.exception;

import lombok.Getter;

/**
 * 代理转发业务异常，携带 HTTP 状态码。
 * 由 ChatProxyService 在前置检查失败时抛出，Controller 捕获后转为对应 HTTP 响应。
 */
@Getter
public class ChatProxyException extends RuntimeException {

    /** HTTP 状态码（如 429、402） */
    private final int httpStatus;

    public ChatProxyException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }
}
