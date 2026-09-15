package com.evops.common;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 解析每次写入请求携带的审计头：
 *   X-Request-Id  请求号（缺省服务端生成）
 *   X-Operator    操作人（缺省 system）
 *   X-Biz-Time    业务时间，ISO 格式 yyyy-MM-dd'T'HH:mm:ss（缺省当前时间）
 */
@Component("ledgerRequestContextFilter")
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_OPERATOR = "X-Operator";
    public static final String HEADER_BIZ_TIME = "X-Biz-Time";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestNo = request.getHeader(HEADER_REQUEST_ID);
            if (!StringUtils.hasText(requestNo)) {
                requestNo = RequestContext.newRequestNo();
            }
            String operator = request.getHeader(HEADER_OPERATOR);
            if (!StringUtils.hasText(operator)) {
                operator = "system";
            }
            LocalDateTime bizTime = parseBizTime(request.getHeader(HEADER_BIZ_TIME));
            RequestContext.set(requestNo, operator, bizTime);
            response.setHeader(HEADER_REQUEST_ID, requestNo);
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private LocalDateTime parseBizTime(String header) {
        if (StringUtils.hasText(header)) {
            try {
                return LocalDateTime.parse(header, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ignored) {
                // 头部格式非法时回落到当前时间
            }
        }
        return LocalDateTime.now();
    }
}
