package com.evops.ledger.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evops.common.ApiResponse;
import com.evops.ledger.dto.CreateOrderRequest;
import com.evops.ledger.dto.TransitionRequest;
import com.evops.ledger.entity.WashOrder;
import com.evops.ledger.service.WashOrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wash/orders")
public class WashOrderController {

    private final WashOrderService orderService;

    public WashOrderController(WashOrderService orderService) {
        this.orderService = orderService;
    }

    /** 工单建档：主表 + 明细同一事务落库；同单号重复提交幂等返回已存在单。 */
    @PostMapping
    public ApiResponse<Object> create(@Validated @RequestBody CreateOrderRequest req) {
        try {
            return ApiResponse.ok(orderService.create(req));
        } catch (DuplicateKeyException e) {
            if (StringUtils.hasText(req.getOrderNo())) {
                return ApiResponse.ok(orderService.detail(req.getOrderNo()));
            }
            throw e;
        }
    }

    /** 按工单号检索（主表 + 明细）。 */
    @GetMapping("/{orderNo}")
    public ApiResponse<Object> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.detail(orderNo));
    }

    @GetMapping
    public ApiResponse<Page<WashOrder>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String storeCode) {
        return ApiResponse.ok(orderService.page(current, size, status, storeCode));
    }

    /** 状态流转：START / FINISH / REDEEM / VOID；越级迁移拒绝并留痕。 */
    @PostMapping("/{orderNo}/transitions")
    public ApiResponse<WashOrder> transition(@PathVariable String orderNo,
                                             @Validated @RequestBody TransitionRequest req) {
        return ApiResponse.ok(orderService.transition(orderNo, req.getAction(), req.getReason()));
    }

    /** 追溯链：流转留痕 + 反向记录 + 卡扣次记录。 */
    @GetMapping("/{orderNo}/trace")
    public ApiResponse<Map<String, Object>> trace(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.trace(orderNo));
    }

    /** 删除只打标记：主表与明细同事务置删除标记。 */
    @DeleteMapping("/{orderNo}")
    public ApiResponse<Void> softDelete(@PathVariable String orderNo) {
        orderService.softDelete(orderNo);
        return ApiResponse.ok(null);
    }
}
