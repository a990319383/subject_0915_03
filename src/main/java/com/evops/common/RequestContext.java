package com.evops.common;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 请求上下文：每次写入请求的请求号、操作人、业务时间。
 * 由 RequestContextFilter 在请求进入时填充，线程结束后清理。
 */
public class RequestContext {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private final String requestNo;
    private final String operator;
    private final LocalDateTime bizTime;

    private RequestContext(String requestNo, String operator, LocalDateTime bizTime) {
        this.requestNo = requestNo;
        this.operator = operator;
        this.bizTime = bizTime;
    }

    public static void set(String requestNo, String operator, LocalDateTime bizTime) {
        HOLDER.set(new RequestContext(requestNo, operator, bizTime));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String currentRequestNo() {
        RequestContext ctx = HOLDER.get();
        return ctx != null ? ctx.requestNo : newRequestNo();
    }

    public static String currentOperator() {
        RequestContext ctx = HOLDER.get();
        return ctx != null ? ctx.operator : "system";
    }

    public static LocalDateTime currentBizTime() {
        RequestContext ctx = HOLDER.get();
        return ctx != null ? ctx.bizTime : LocalDateTime.now();
    }

    public static String newRequestNo() {
        return "REQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
