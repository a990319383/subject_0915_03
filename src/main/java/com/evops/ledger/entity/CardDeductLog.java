package com.evops.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.AuditLogEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员卡扣次 / 回补记录（append-only）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_card_deduct_log")
public class CardDeductLog extends AuditLogEntity {
    private String cardNo;
    private String orderNo;
    /** DEDUCT=核销扣次 REFUND=作废回补 */
    private String changeType;
    private Integer changeTimes;
    private Integer balanceAfter;
}
