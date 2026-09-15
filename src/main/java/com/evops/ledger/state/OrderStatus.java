package com.evops.ledger.state;

/**
 * 工单状态：已建档 → 服务中 → 已完成 → 已核销；任意非终态可作废。
 */
public enum OrderStatus {
    CREATED,
    SERVING,
    FINISHED,
    REDEEMED,
    VOIDED
}
