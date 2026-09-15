package com.evops.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员卡：按次数扣减，核销一次扣一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_member_card")
public class MemberCard extends AuditEntity {
    private String cardNo;
    private String storeCode;
    private String holderName;
    private String holderPhone;
    private Integer totalTimes;
    private Integer remainingTimes;
    private String status;
}
