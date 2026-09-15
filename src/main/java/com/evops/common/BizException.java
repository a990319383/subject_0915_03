package com.evops.common;

/**
 * 业务异常：非法状态迁移、卡次数不足、记录不存在等。
 * 由 GlobalExceptionHandler 统一转为 ApiResponse.fail。
 */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
