package com.evops.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.AuditLogEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 状态流转留痕：合法迁移与被拒绝的越级迁移都记录（append-only）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wash_order_flow")
public class WashOrderFlow extends AuditLogEntity {
    private String orderNo;
    private String action;
    private String fromStatus;
    private String toStatus;
    /** Y=已接受 N=已拒绝 */
    private String accepted;
    private String rejectReason;
}
