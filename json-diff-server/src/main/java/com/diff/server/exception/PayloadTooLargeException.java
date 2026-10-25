package com.diff.server.exception;

/** 单份 JSON 超过允许的最大长度，对应 HTTP 413。 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
