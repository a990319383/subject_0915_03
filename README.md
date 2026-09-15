# EVOPS 基础工作区

这是 `001-evops` 题包的初始 Java 工作区。它只提供构建、配置、统一返回、异常处理、MyBatis-Plus 和 H2 本地数据库基础设施，不预先实现 F1 的站点、充电枪、会话和结算业务；F1 由执行模型从这里开始完成。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

题包根目录的 `..\..\docs\schema\evops.sql` 是交付副本，应与工作区 `src/main/resources/schema.sql` 保持一致。

题面和质检卷在上一级 `packets/` 目录；模型工作区不得复制 `answers.md`、验收测试或参考修正。

---

# 连锁洗车 · 门店侧底账（F 任务交付）

在骨架之上实现了门店运营底账：工位基础信息、洗车工单建档、核销状态流转、按工单号检索，台账规模 8.5 万行。

## 数据模型（`src/main/resources/schema.sql`）

| 表 | 说明 |
|---|---|
| `t_wash_bay` | 工位基础信息（门店 + 工位号唯一） |
| `t_member_card` | 会员卡（按次数扣减，`remaining_times`） |
| `t_wash_order` | 洗车工单主表（`order_no` 唯一索引，检索键） |
| `t_wash_order_item` | 工单明细，与主表同生共死 |
| `t_wash_order_flow` | 状态流转留痕（含被拒绝的越级迁移，append-only） |
| `t_wash_order_reversal` | 作废反向记录（append-only） |
| `t_card_deduct_log` | 卡扣次 / 回补记录（append-only） |

公共审计列：`request_no`（请求号）、`operator`（操作人）、`biz_time`（业务时间）、
`data_version`（数据版本，乐观锁）、`deleted`（删除标记，只打标记不物理删）。
每次写入（含更新）都刷新这四项；日志型表只插不改，构成追溯链。

## 业务规则

- **状态机**：`CREATED → SERVING → FINISHED → REDEEMED`；任意非终态可 `VOID`（作废）。
  越级迁移直接拒绝，并在 `t_wash_order_flow` 留痕（`accepted=N` + 拒绝原因）。
- **建档**：主表 + 明细 + 建档留痕同一事务，任何一步失败整体回滚，不允许半截落库；
  同 `order_no` 重复提交幂等返回已存在单。
- **核销**：`FINISHED → REDEEMED` 时卡内次数 `-1`（CAS 扣减，次数不足/卡不可用拒绝并回滚），
  写 `DEDUCT` 扣次记录。
- **作废**：不删不改历史，新增反向记录 `t_wash_order_reversal`；已核销工单作废自动
  回补卡次数 `+1` 并写 `REFUND` 记录。
- **删除**：`DELETE` 接口只对主表/明细打 `deleted=1` 标记，追溯链（留痕/反向/扣次）保留可查。

## API 一览（`/api/**` 匿名可访问）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/wash/bays` | 工位建档 |
| GET | `/api/wash/bays?storeCode=` | 工位列表 |
| DELETE | `/api/wash/bays/{storeCode}/{bayCode}` | 工位软删 |
| POST | `/api/wash/cards` | 会员卡开卡 |
| GET | `/api/wash/cards/{cardNo}` | 卡查询（含剩余次数） |
| POST | `/api/wash/orders` | 工单建档（主+明细，事务） |
| GET | `/api/wash/orders/{orderNo}` | 按工单号检索（主+明细） |
| GET | `/api/wash/orders?current=&size=&status=&storeCode=` | 分页检索 |
| POST | `/api/wash/orders/{orderNo}/transitions` | 状态流转 `{action: START/FINISH/REDEEM/VOID, reason?}` |
| GET | `/api/wash/orders/{orderNo}/trace` | 追溯链（留痕+反向+扣次） |
| DELETE | `/api/wash/orders/{orderNo}` | 工单软删（主+明细同事务打标） |

请求头（可选）：`X-Request-Id` 请求号、`X-Operator` 操作人、`X-Biz-Time` 业务时间
（`yyyy-MM-dd'T'HH:mm:ss`）；缺省时服务端生成/填充。

## 台账种子（8.5 万行）

```bash
mvn -q -DskipTests package
java -jar target/evops-base-0.1.0-SNAPSHOT.jar --evops.seed.enabled=true
```

灌入 20 工位 / 5,000 会员卡 / 85,000 工单（+明细/留痕/反向/扣次，共约 54 万行），
约 15 秒完成；按主表行数断点续灌，达标自动跳过。规模参数见 `application.yml` 的 `evops.seed.*`。

## 压测

```bash
python3 scripts/loadtest.py --mode create --concurrency 180 --requests 3600   # 建档
python3 scripts/loadtest.py --mode query  --concurrency 180 --requests 18000  # 检索
```

180 并发两轮结果与台账核验见 `docs/loadtest-report.md`。
