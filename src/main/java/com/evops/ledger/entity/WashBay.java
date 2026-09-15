package com.evops.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.AuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工位基础信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_wash_bay")
public class WashBay extends AuditEntity {
    private String storeCode;
    private String bayCode;
    private String bayName;
    private String bayType;
    private String status;
}
