package com.diff.server.exception;

import com.diff.server.model.DiffResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理：无论成功失败都返回同一个 {@link DiffResponse} 信封，
 * 前端只需判断 {@code status} 即可。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 请求体本身不是合法 JSON / 结构对不上 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DiffResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(DiffResponse.error("请求体不是合法 JSON"));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<DiffResponse> handleInvalidRequest(InvalidRequestException e) {
        return ResponseEntity.badRequest().body(DiffResponse.error(e.getMessage()));
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<DiffResponse> handlePayloadTooLarge(PayloadTooLargeException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(DiffResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DiffResponse> handleUnexpected(Exception e) {
        log.error("JSON 对比服务内部错误", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DiffResponse.error("服务内部错误: " + e.getMessage()));
    }
}
