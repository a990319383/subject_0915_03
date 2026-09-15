package com.evops.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evops.common.BizException;
import com.evops.common.RequestContext;
import com.evops.ledger.dto.CreateOrderRequest;
import com.evops.ledger.entity.CardDeductLog;
import com.evops.ledger.entity.MemberCard;
import com.evops.ledger.entity.WashBay;
import com.evops.ledger.entity.WashOrder;
import com.evops.ledger.entity.WashOrderFlow;
import com.evops.ledger.entity.WashOrderItem;
import com.evops.ledger.entity.WashOrderReversal;
import com.evops.ledger.mapper.CardDeductLogMapper;
import com.evops.ledger.mapper.MemberCardMapper;
import com.evops.ledger.mapper.WashBayMapper;
import com.evops.ledger.mapper.WashOrderFlowMapper;
import com.evops.ledger.mapper.WashOrderItemMapper;
import com.evops.ledger.mapper.WashOrderMapper;
import com.evops.ledger.mapper.WashOrderReversalMapper;
import com.evops.ledger.state.OrderAction;
import com.evops.ledger.state.OrderStateMachine;
import com.evops.ledger.state.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 洗车工单核心服务：建档 / 状态流转 / 核销扣卡 / 作废反向记录 / 检索 / 软删。
 *
 * 事务约定：
 *  - 建档：主表 + 明细 + 建档留痕同一事务，任何一步失败整体回滚，不允许半截落库；
 *  - 流转：状态 CAS + 卡扣减/回补 + 留痕 + 反向记录同一事务；
 *  - 越级迁移：先经 FlowRecorder 独立事务留痕，再抛异常拒绝。
 */
@Service
public class WashOrderService {

    private final WashOrderMapper orderMapper;
    private final WashOrderItemMapper itemMapper;
    private final WashOrderFlowMapper flowMapper;
    private final WashOrderReversalMapper reversalMapper;
    private final CardDeductLogMapper deductLogMapper;
    private final WashBayMapper bayMapper;
    private final MemberCardMapper cardMapper;
    private final FlowRecorder flowRecorder;

    public WashOrderService(WashOrderMapper orderMapper, WashOrderItemMapper itemMapper,
                            WashOrderFlowMapper flowMapper, WashOrderReversalMapper reversalMapper,
                            CardDeductLogMapper deductLogMapper, WashBayMapper bayMapper,
                            MemberCardMapper cardMapper, FlowRecorder flowRecorder) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.flowMapper = flowMapper;
        this.reversalMapper = reversalMapper;
        this.deductLogMapper = deductLogMapper;
        this.bayMapper = bayMapper;
        this.cardMapper = cardMapper;
        this.flowRecorder = flowRecorder;
    }

    // ---------------------------------------------------------------- 建档

    @Transactional
    public Map<String, Object> create(CreateOrderRequest req) {
        Long bayId = null;
        if (StringUtils.hasText(req.getBayCode())) {
            WashBay bay = bayMapper.selectOne(new LambdaQueryWrapper<WashBay>()
                    .eq(WashBay::getStoreCode, req.getStoreCode())
                    .eq(WashBay::getBayCode, req.getBayCode()));
            if (bay == null) {
                throw new BizException("工位不存在: " + req.getStoreCode() + "/" + req.getBayCode());
            }
            if (!"ENABLED".equals(bay.getStatus())) {
                throw new BizException("工位已停用: " + req.getBayCode());
            }
            bayId = bay.getId();
        }
        if (StringUtils.hasText(req.getCardNo())) {
            MemberCard card = cardMapper.selectOne(new LambdaQueryWrapper<MemberCard>()
                    .eq(MemberCard::getCardNo, req.getCardNo()));
            if (card == null) {
                throw new BizException("会员卡不存在: " + req.getCardNo());
            }
            if (!"ACTIVE".equals(card.getStatus())) {
                throw new BizException("会员卡状态不可用: " + req.getCardNo());
            }
        }

        String orderNo = StringUtils.hasText(req.getOrderNo()) ? req.getOrderNo() : generateOrderNo();

        WashOrder order = new WashOrder();
        order.setOrderNo(orderNo);
        order.setStoreCode(req.getStoreCode());
        order.setBayId(bayId);
        order.setBayCode(req.getBayCode());
        order.setCardNo(req.getCardNo());
        order.setPlateNo(req.getPlateNo());
        order.setStatus(OrderStatus.CREATED.name());

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.Item item : req.getItems()) {
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())));
        }
        order.setTotalAmount(total);
        orderMapper.insert(order);

        for (CreateOrderRequest.Item item : req.getItems()) {
            WashOrderItem row = new WashOrderItem();
            row.setOrderNo(orderNo);
            row.setItemName(item.getItemName());
            row.setItemType(StringUtils.hasText(item.getItemType()) ? item.getItemType() : "WASH");
            row.setQty(item.getQty());
            row.setUnitPrice(item.getUnitPrice());
            row.setAmount(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty())));
            itemMapper.insert(row);
        }

        recordFlow(orderNo, "CREATE", null, OrderStatus.CREATED.name(), "Y", null);
        return detail(orderNo);
    }

    // ---------------------------------------------------------------- 状态流转

    @Transactional
    public WashOrder transition(String orderNo, String actionText, String reason) {
        WashOrder order = mustGet(orderNo);
        OrderStatus from = OrderStatus.valueOf(order.getStatus());

        OrderAction action;
        try {
            action = OrderAction.valueOf(actionText.trim().toUpperCase());
        } catch (Exception e) {
            flowRecorder.recordRejected(orderNo, actionText, from.name(), null, "未知动作: " + actionText);
            throw new BizException("未知动作: " + actionText);
        }

        OrderStatus to = OrderStateMachine.resolve(action, from);
        if (to == null) {
            String why = "越级迁移被拒绝: 当前状态 " + from + " 不允许动作 " + action
                    + " (当前允许: " + OrderStateMachine.allowedActions(from) + ")";
            flowRecorder.recordRejected(orderNo, action.name(), from.name(),
                    action.nominalTarget().name(), why);
            throw new BizException(why);
        }

        // 状态 CAS：并发下只有一方生效，失败方整体回滚
        int cas = orderMapper.casUpdateStatus(orderNo, from.name(), to.name(),
                RequestContext.currentRequestNo(), RequestContext.currentOperator(),
                RequestContext.currentBizTime());
        if (cas == 0) {
            throw new BizException("工单状态已被并发修改，请刷新后重试: " + orderNo);
        }

        if (action == OrderAction.REDEEM) {
            redeemCard(order);
        } else if (action == OrderAction.VOID) {
            writeReversal(orderNo, from, reason, from == OrderStatus.REDEEMED);
            if (from == OrderStatus.REDEEMED) {
                refundCard(order);
            }
        }

        recordFlow(orderNo, action.name(), from.name(), to.name(), "Y", reason);
        return mustGet(orderNo);
    }

    /** 核销：卡内次数 -1，并写扣次记录。次数不足 / 卡不可用直接拒绝。 */
    private void redeemCard(WashOrder order) {
        if (!StringUtils.hasText(order.getCardNo())) {
            throw new BizException("工单未关联会员卡，无法核销: " + order.getOrderNo());
        }
        String cardNo = order.getCardNo();
        for (int attempt = 0; attempt < 3; attempt++) {
            MemberCard card = cardMapper.selectOne(new LambdaQueryWrapper<MemberCard>()
                    .eq(MemberCard::getCardNo, cardNo));
            if (card == null) {
                throw new BizException("会员卡不存在: " + cardNo);
            }
            if (!"ACTIVE".equals(card.getStatus())) {
                throw new BizException("会员卡状态不可用: " + cardNo);
            }
            if (card.getRemainingTimes() == null || card.getRemainingTimes() <= 0) {
                throw new BizException("卡内次数不足，无法核销: " + cardNo);
            }
            int updated = cardMapper.deductOnce(cardNo, card.getRemainingTimes(),
                    RequestContext.currentRequestNo(), RequestContext.currentOperator(),
                    RequestContext.currentBizTime());
            if (updated == 1) {
                recordDeductLog(cardNo, order.getOrderNo(), "DEDUCT", -1, card.getRemainingTimes() - 1);
                return;
            }
        }
        throw new BizException("会员卡并发扣减冲突，请重试: " + cardNo);
    }

    /** 已核销工单作废：卡内次数 +1 回补，并写回补记录。 */
    private void refundCard(WashOrder order) {
        String cardNo = order.getCardNo();
        if (!StringUtils.hasText(cardNo)) {
            return;
        }
        MemberCard card = cardMapper.selectOne(new LambdaQueryWrapper<MemberCard>()
                .eq(MemberCard::getCardNo, cardNo));
        if (card == null) {
            throw new BizException("关联会员卡不存在，无法回补: " + cardNo);
        }
        int updated = cardMapper.refundOnce(cardNo,
                RequestContext.currentRequestNo(), RequestContext.currentOperator(),
                RequestContext.currentBizTime());
        if (updated == 0) {
            throw new BizException("会员卡回补失败（卡已删除或次数异常）: " + cardNo);
        }
        recordDeductLog(cardNo, order.getOrderNo(), "REFUND", 1, card.getRemainingTimes() + 1);
    }

    /** 作废反向记录：不删不改原单历史，新增一条反向记录构成追溯链。 */
    private void writeReversal(String orderNo, OrderStatus reversedStatus, String reason, boolean compensate) {
        WashOrderReversal reversal = new WashOrderReversal();
        reversal.setReversalNo("RV" + orderNo);
        reversal.setOrderNo(orderNo);
        reversal.setReversedStatus(reversedStatus.name());
        reversal.setCompensateCard(compensate ? "Y" : "N");
        reversal.setReason(reason);
        reversalMapper.insert(reversal);
    }

    // ---------------------------------------------------------------- 检索

    public Map<String, Object> detail(String orderNo) {
        WashOrder order = mustGet(orderNo);
        List<WashOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<WashOrderItem>()
                .eq(WashOrderItem::getOrderNo, orderNo)
                .orderByAsc(WashOrderItem::getId));
        Map<String, Object> view = new HashMap<>();
        view.put("order", order);
        view.put("items", items);
        return view;
    }

    public Page<WashOrder> page(long current, long size, String status, String storeCode) {
        LambdaQueryWrapper<WashOrder> wrapper = new LambdaQueryWrapper<WashOrder>()
                .eq(StringUtils.hasText(status), WashOrder::getStatus, status)
                .eq(StringUtils.hasText(storeCode), WashOrder::getStoreCode, storeCode)
                .orderByDesc(WashOrder::getId);
        return orderMapper.selectPage(new Page<>(current, size), wrapper);
    }

    /** 追溯链：流转留痕（含被拒记录）+ 反向记录 + 卡扣次记录。 */
    public Map<String, Object> trace(String orderNo) {
        List<WashOrderFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<WashOrderFlow>()
                .eq(WashOrderFlow::getOrderNo, orderNo)
                .orderByAsc(WashOrderFlow::getId));
        List<WashOrderReversal> reversals = reversalMapper.selectList(
                new LambdaQueryWrapper<WashOrderReversal>()
                        .eq(WashOrderReversal::getOrderNo, orderNo)
                        .orderByAsc(WashOrderReversal::getId));
        List<CardDeductLog> deductLogs = deductLogMapper.selectList(
                new LambdaQueryWrapper<CardDeductLog>()
                        .eq(CardDeductLog::getOrderNo, orderNo)
                        .orderByAsc(CardDeductLog::getId));
        Map<String, Object> view = new HashMap<>();
        view.put("orderNo", orderNo);
        view.put("flows", flows);
        view.put("reversals", reversals);
        view.put("deductLogs", deductLogs);
        return view;
    }

    // ---------------------------------------------------------------- 软删

    /** 删除只打标记：主表与明细同一事务置 deleted=1，不物理删除。 */
    @Transactional
    public void softDelete(String orderNo) {
        WashOrder order = mustGet(orderNo);
        orderMapper.deleteById(order.getId());
        itemMapper.delete(new LambdaQueryWrapper<WashOrderItem>()
                .eq(WashOrderItem::getOrderNo, orderNo));
    }

    // ---------------------------------------------------------------- 内部

    private WashOrder mustGet(String orderNo) {
        WashOrder order = orderMapper.selectOne(new LambdaQueryWrapper<WashOrder>()
                .eq(WashOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException("工单不存在或已删除: " + orderNo);
        }
        return order;
    }

    private void recordFlow(String orderNo, String action, String from, String to,
                            String accepted, String rejectReason) {
        WashOrderFlow flow = new WashOrderFlow();
        flow.setOrderNo(orderNo);
        flow.setAction(action);
        flow.setFromStatus(from);
        flow.setToStatus(to);
        flow.setAccepted(accepted);
        flow.setRejectReason(rejectReason);
        flowMapper.insert(flow);
    }

    private void recordDeductLog(String cardNo, String orderNo, String changeType,
                                 int changeTimes, int balanceAfter) {
        CardDeductLog log = new CardDeductLog();
        log.setCardNo(cardNo);
        log.setOrderNo(orderNo);
        log.setChangeType(changeType);
        log.setChangeTimes(changeTimes);
        log.setBalanceAfter(balanceAfter);
        deductLogMapper.insert(log);
    }

    private String generateOrderNo() {
        return "WO" + System.currentTimeMillis()
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
