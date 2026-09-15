package com.evops.ledger.service;

import com.evops.ledger.entity.WashOrderFlow;
import com.evops.ledger.mapper.WashOrderFlowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 流转留痕记录器。
 * 被拒绝的越级迁移必须留在台账里，即使外层事务回滚也不能丢，
 * 因此用 REQUIRES_NEW 独立事务提交（编程式，避免 AOP 代理干扰）。
 */
@Service
public class FlowRecorder {

    private final WashOrderFlowMapper flowMapper;
    private final TransactionTemplate requiresNewTx;

    public FlowRecorder(WashOrderFlowMapper flowMapper, PlatformTransactionManager tm) {
        this.flowMapper = flowMapper;
        this.requiresNewTx = new TransactionTemplate(tm);
        this.requiresNewTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recordRejected(String orderNo, String action, String fromStatus,
                               String attemptedTo, String reason) {
        requiresNewTx.executeWithoutResult(status -> {
            WashOrderFlow flow = new WashOrderFlow();
            flow.setOrderNo(orderNo);
            flow.setAction(action != null ? action : "UNKNOWN");
            flow.setFromStatus(fromStatus);
            flow.setToStatus(attemptedTo != null ? attemptedTo : "UNKNOWN");
            flow.setAccepted("N");
            flow.setRejectReason(reason);
            flowMapper.insert(flow);
        });
    }
}
