package com.evops.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 台账种子数据：按配置规模（默认 8.5 万工单）批量灌入
 * 工位 / 会员卡 / 工单主表 / 明细 / 流转留痕 / 反向记录 / 扣次记录。
 *
 * 启动方式：--evops.seed.enabled=true
 * 幂等：按工单号确定性生成，已达标则跳过；中断后按主表行数续灌。
 */
@Component
@ConditionalOnProperty(prefix = "evops.seed", name = "enabled", havingValue = "true")
public class LedgerSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LedgerSeedRunner.class);
    private static final String SEED_REQUEST_NO = "SEED-20260915";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Value("${evops.seed.order-count:85000}")
    private int orderCount;
    @Value("${evops.seed.store-count:2}")
    private int storeCount;
    @Value("${evops.seed.bays-per-store:10}")
    private int baysPerStore;
    @Value("${evops.seed.card-count:5000}")
    private int cardCount;
    @Value("${evops.seed.card-times:30}")
    private int cardTimes;
    @Value("${evops.seed.batch-size:1000}")
    private int batchSize;

    public LedgerSeedRunner(JdbcTemplate jdbcTemplate,
                            org.springframework.transaction.PlatformTransactionManager tm) {
        this.jdbc = jdbcTemplate;
        this.tx = new TransactionTemplate(tm);
    }

    @Override
    public void run(ApplicationArguments args) {
        long started = System.currentTimeMillis();
        log.info("[seed] 台账种子开始，目标工单数={}", orderCount);

        seedBaysAndCards();

        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM t_wash_order", Long.class);
        int from = existing == null ? 0 : existing.intValue();
        if (from >= orderCount) {
            log.info("[seed] 已有 {} 行工单，达到目标 {}，跳过", from, orderCount);
            return;
        }
        if (from > 0) {
            log.info("[seed] 检测到已有 {} 行，从第 {} 行续灌", from, from + 1);
        }

        // 每张开卡剩余次数在灌单过程中同步扣减，最后统一回写会员卡
        Map<String, Integer> cardRemaining = new HashMap<>();
        jdbc.query("SELECT card_no, remaining_times FROM t_member_card",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> cardRemaining.put(rs.getString(1), rs.getInt(2)));

        LocalDateTime base = LocalDateTime.now();
        int done = from;
        while (done < orderCount) {
            final int chunkStart = done;
            final int chunkEnd = Math.min(done + batchSize, orderCount);
            tx.executeWithoutResult(s -> insertChunk(chunkStart, chunkEnd, base, cardRemaining));
            done = chunkEnd;
            if (done % 5000 == 0 || done == orderCount) {
                log.info("[seed] 工单进度 {}/{}", done, orderCount);
            }
        }

        jdbc.batchUpdate("UPDATE t_member_card SET remaining_times = ? WHERE card_no = ?",
                toBatchArgs(cardRemaining));
        log.info("[seed] 台账种子完成，工单={}，耗时={}ms", orderCount, System.currentTimeMillis() - started);
    }

    // ------------------------------------------------------------------

    private void seedBaysAndCards() {
        Long bays = jdbc.queryForObject("SELECT COUNT(*) FROM t_wash_bay WHERE request_no = ?",
                Long.class, SEED_REQUEST_NO);
        if (bays == null || bays == 0) {
            List<Object[]> rows = new ArrayList<>();
            for (int s = 1; s <= storeCount; s++) {
                String store = storeCode(s);
                for (int b = 1; b <= baysPerStore; b++) {
                    rows.add(new Object[]{store, String.format("B%02d", b),
                            "工位-" + store + "-" + b, "STANDARD", "ENABLED"});
                }
            }
            jdbc.batchUpdate("INSERT INTO t_wash_bay(store_code,bay_code,bay_name,bay_type,status,"
                    + "request_no,operator,biz_time,data_version,deleted,create_time,update_time) "
                    + "VALUES (?,?,?,?,?,'" + SEED_REQUEST_NO + "','seeder',CURRENT_TIMESTAMP,0,0,"
                    + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", rows);
            log.info("[seed] 工位 {} 行", rows.size());
        }

        Long cards = jdbc.queryForObject("SELECT COUNT(*) FROM t_member_card WHERE request_no = ?",
                Long.class, SEED_REQUEST_NO);
        if (cards == null || cards == 0) {
            List<Object[]> rows = new ArrayList<>();
            for (int c = 1; c <= cardCount; c++) {
                rows.add(new Object[]{cardNo(c), storeCode((c % storeCount) + 1),
                        "会员" + c, "138" + String.format("%08d", c), cardTimes, cardTimes, "ACTIVE"});
            }
            for (int i = 0; i < rows.size(); i += batchSize) {
                jdbc.batchUpdate("INSERT INTO t_member_card(card_no,store_code,holder_name,holder_phone,"
                        + "total_times,remaining_times,status,request_no,operator,biz_time,"
                        + "data_version,deleted,create_time,update_time) VALUES (?,?,?,?,?,?,?,"
                        + "'" + SEED_REQUEST_NO + "','seeder',CURRENT_TIMESTAMP,0,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                        rows.subList(i, Math.min(i + batchSize, rows.size())));
            }
            log.info("[seed] 会员卡 {} 行", rows.size());
        }
    }

    private void insertChunk(int fromInclusive, int toExclusive, LocalDateTime base,
                             Map<String, Integer> cardRemaining) {
        List<Object[]> orders = new ArrayList<>();
        List<Object[]> items = new ArrayList<>();
        List<Object[]> flows = new ArrayList<>();
        List<Object[]> deducts = new ArrayList<>();
        List<Object[]> reversals = new ArrayList<>();

        for (int i = fromInclusive; i < toExclusive; i++) {
            int seq = i + 1;
            String orderNo = orderNo(seq);
            String store = storeCode((i % storeCount) + 1);
            String bay = String.format("B%02d", (i % baysPerStore) + 1);
            String card = cardNo((i % cardCount) + 1);
            String plate = "沪A" + String.format("%05d", seq % 100000);
            String status = statusOf(i);
            LocalDateTime biz = base.minusDays(i % 90).minusSeconds(i % 86400);
            Timestamp ts = Timestamp.valueOf(biz);
            BigDecimal p1 = BigDecimal.valueOf(20L + (i % 5) * 5L);
            BigDecimal p2 = BigDecimal.valueOf(15);
            BigDecimal total = p1.add(p2);

            orders.add(new Object[]{orderNo, store, bay, card, plate, status, total,
                    SEED_REQUEST_NO, "seeder", ts, 0, 0, ts, ts});
            items.add(new Object[]{orderNo, "外观精洗", "WASH", 1, p1, p1,
                    SEED_REQUEST_NO, "seeder", ts, 0, 0, ts, ts});
            items.add(new Object[]{orderNo, "内饰清洁", "WASH", 1, p2, p2,
                    SEED_REQUEST_NO, "seeder", ts, 0, 0, ts, ts});

            addFlow(flows, orderNo, "CREATE", null, "CREATED", ts);
            if ("SERVING".equals(status) || "FINISHED".equals(status) || "REDEEMED".equals(status)) {
                addFlow(flows, orderNo, "START", "CREATED", "SERVING", ts);
            }
            if ("FINISHED".equals(status) || "REDEEMED".equals(status)) {
                addFlow(flows, orderNo, "FINISH", "SERVING", "FINISHED", ts);
            }
            if ("REDEEMED".equals(status)) {
                addFlow(flows, orderNo, "REDEEM", "FINISHED", "REDEEMED", ts);
                Integer cur = cardRemaining.get(card);
                if (cur != null) {
                    cardRemaining.put(card, cur - 1);
                    deducts.add(new Object[]{card, orderNo, "DEDUCT", -1, cur - 1,
                            SEED_REQUEST_NO, "seeder", ts, ts});
                }
            }
            if ("VOIDED".equals(status)) {
                addFlow(flows, orderNo, "VOID", "CREATED", "VOIDED", ts);
                reversals.add(new Object[]{"RV" + orderNo, orderNo, "CREATED", "N", "种子作废",
                        SEED_REQUEST_NO, "seeder", ts, ts});
            }
        }

        jdbc.batchUpdate("INSERT INTO t_wash_order(order_no,store_code,bay_code,card_no,plate_no,"
                + "status,total_amount,request_no,operator,biz_time,data_version,deleted,"
                + "create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", orders);
        jdbc.batchUpdate("INSERT INTO t_wash_order_item(order_no,item_name,item_type,qty,"
                + "unit_price,amount,request_no,operator,biz_time,data_version,deleted,"
                + "create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", items);
        jdbc.batchUpdate("INSERT INTO t_wash_order_flow(order_no,action,from_status,to_status,"
                + "accepted,request_no,operator,biz_time,create_time) VALUES (?,?,?,?,'Y',?,?,?,?)", flows);
        if (!deducts.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO t_card_deduct_log(card_no,order_no,change_type,"
                    + "change_times,balance_after,request_no,operator,biz_time,create_time) "
                    + "VALUES (?,?,?,?,?,?,?,?,?)", deducts);
        }
        if (!reversals.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO t_wash_order_reversal(reversal_no,order_no,reversed_status,"
                    + "compensate_card,reason,request_no,operator,biz_time,create_time) "
                    + "VALUES (?,?,?,?,?,?,?,?,?)", reversals);
        }
    }

    private void addFlow(List<Object[]> flows, String orderNo, String action,
                         String from, String to, Timestamp ts) {
        flows.add(new Object[]{orderNo, action, from, to, SEED_REQUEST_NO, "seeder", ts, ts});
    }

    /** 确定性状态分布：CREATED/SERVING 各 10%，FINISHED 20%，REDEEMED 55%，VOIDED 5%。 */
    private String statusOf(int i) {
        int bucket = i % 20;
        if (bucket < 2) {
            return "CREATED";
        }
        if (bucket < 4) {
            return "SERVING";
        }
        if (bucket < 8) {
            return "FINISHED";
        }
        if (bucket < 19) {
            return "REDEEMED";
        }
        return "VOIDED";
    }

    private String storeCode(int s) {
        return String.format("S%03d", s);
    }

    private String cardNo(int c) {
        return "CARD" + String.format("%06d", c);
    }

    private String orderNo(int seq) {
        return "WO" + String.format("%010d", seq);
    }

    private List<Object[]> toBatchArgs(Map<String, Integer> cardRemaining) {
        List<Object[]> args = new ArrayList<>(cardRemaining.size());
        cardRemaining.forEach((cardNo, remaining) -> args.add(new Object[]{remaining, cardNo}));
        return args;
    }
}
