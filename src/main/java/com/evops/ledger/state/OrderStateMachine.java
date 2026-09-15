package com.evops.ledger.state;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机：只允许合法路径迁移，越级迁移由调用方拒绝并留痕。
 *
 *   CREATED  --START-->  SERVING  --FINISH-->  FINISHED  --REDEEM-->  REDEEMED
 *   CREATED/SERVING/FINISHED/REDEEMED --VOID--> VOIDED（终态）
 *
 * VOIDED 为终态，REDEMEED 除作废外不可再迁移。
 */
public final class OrderStateMachine {

    private static final Map<OrderAction, Map<OrderStatus, OrderStatus>> TRANSITIONS;

    static {
        Map<OrderAction, Map<OrderStatus, OrderStatus>> t = new HashMap<>();

        Map<OrderStatus, OrderStatus> start = new HashMap<>();
        start.put(OrderStatus.CREATED, OrderStatus.SERVING);
        t.put(OrderAction.START, start);

        Map<OrderStatus, OrderStatus> finish = new HashMap<>();
        finish.put(OrderStatus.SERVING, OrderStatus.FINISHED);
        t.put(OrderAction.FINISH, finish);

        Map<OrderStatus, OrderStatus> redeem = new HashMap<>();
        redeem.put(OrderStatus.FINISHED, OrderStatus.REDEEMED);
        t.put(OrderAction.REDEEM, redeem);

        Map<OrderStatus, OrderStatus> voided = new HashMap<>();
        voided.put(OrderStatus.CREATED, OrderStatus.VOIDED);
        voided.put(OrderStatus.SERVING, OrderStatus.VOIDED);
        voided.put(OrderStatus.FINISHED, OrderStatus.VOIDED);
        voided.put(OrderStatus.REDEEMED, OrderStatus.VOIDED);
        t.put(OrderAction.VOID, voided);

        TRANSITIONS = t;
    }

    private OrderStateMachine() {
    }

    /**
     * 计算目标状态；迁移非法时返回 null。
     */
    public static OrderStatus resolve(OrderAction action, OrderStatus from) {
        Map<OrderStatus, OrderStatus> byFrom = TRANSITIONS.get(action);
        if (byFrom == null) {
            return null;
        }
        return byFrom.get(from);
    }

    /**
     * 某状态下允许的动作（用于错误提示）。
     */
    public static Set<OrderAction> allowedActions(OrderStatus from) {
        Set<OrderAction> allowed = EnumSet.noneOf(OrderAction.class);
        for (Map.Entry<OrderAction, Map<OrderStatus, OrderStatus>> e : TRANSITIONS.entrySet()) {
            if (e.getValue().containsKey(from)) {
                allowed.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(allowed);
    }
}
