package com.diff.server.exception;

/** 请求不合法（JSON 解析失败、两侧都为空等），对应 HTTP 400。 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
