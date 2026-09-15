package com.evops.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 洗车工单明细，与主表同生共死。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wash_order_item")
public class WashOrderItem extends AuditEntity {
    private String orderNo;
    private String itemName;
    private String itemType;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
