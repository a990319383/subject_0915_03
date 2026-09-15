package com.evops.ledger.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 工单建档请求：主表信息 + 明细列表，同一事务落库。
 */
@Data
public class CreateOrderRequest {

    /** 工单号，可空；为空时服务端生成。重复单号幂等返回已存在单。 */
    @Size(max = 32)
    private String orderNo;

    @NotBlank
    @Size(max = 32)
    private String storeCode;

    @Size(max = 32)
    private String bayCode;

    @Size(max = 32)
    private String cardNo;

    @Size(max = 16)
    private String plateNo;

    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank
        @Size(max = 64)
        private String itemName;

        @Size(max = 16)
        private String itemType;

        @NotNull
        @Positive
        private Integer qty;

        @NotNull
        @PositiveOrZero
        private BigDecimal unitPrice;
    }
}
