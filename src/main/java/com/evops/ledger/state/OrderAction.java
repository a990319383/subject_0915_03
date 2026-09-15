package com.evops.ledger.state;

/**
 * 工单动作：开始服务 / 服务完成 / 核销 / 作废。
 * nominalTarget 用于留痕时记录动作的目标状态（含被拒绝的越级尝试）。
 */
public enum OrderAction {
    START(OrderStatus.SERVING),
    FINISH(OrderStatus.FINISHED),
    REDEEM(OrderStatus.REDEEMED),
    VOID(OrderStatus.VOIDED);

    private final OrderStatus nominalTarget;

    OrderAction(OrderStatus nominalTarget) {
        this.nominalTarget = nominalTarget;
    }

    public OrderStatus nominalTarget() {
        return nominalTarget;
    }
}
