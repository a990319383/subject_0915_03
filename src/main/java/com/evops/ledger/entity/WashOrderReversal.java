package com.evops.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.AuditLogEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作废反向记录：作废不删原单，新增一条反向记录构成追溯链（append-only）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wash_order_reversal")
public class WashOrderReversal extends AuditLogEntity {
    private String reversalNo;
    private String orderNo;
    /** 作废时工单所处状态 */
    private String reversedStatus;
    /** 是否回补了会员卡次数 */
    private String compensateCard;
    private String reason;
}
