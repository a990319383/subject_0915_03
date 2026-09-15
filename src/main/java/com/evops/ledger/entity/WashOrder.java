package com.evops.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 洗车工单主表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wash_order")
public class WashOrder extends AuditEntity {
    private String orderNo;
    private String storeCode;
    private Long bayId;
    private String bayCode;
    private String cardNo;
    private String plateNo;
    private String status;
    private BigDecimal totalAmount;
}
