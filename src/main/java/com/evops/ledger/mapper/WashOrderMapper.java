package com.evops.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.ledger.entity.WashOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface WashOrderMapper extends BaseMapper<WashOrder> {

    /**
     * 状态迁移 CAS：仅当当前状态与预期一致时落库，并发下只有一方生效。
     * 同时刷新请求号 / 操作人 / 业务时间 / 数据版本。
     */
    @Update("UPDATE t_wash_order SET status = #{toStatus}, "
            + "data_version = data_version + 1, request_no = #{requestNo}, operator = #{operator}, "
            + "biz_time = #{bizTime}, update_time = CURRENT_TIMESTAMP "
            + "WHERE order_no = #{orderNo} AND status = #{fromStatus} AND deleted = 0")
    int casUpdateStatus(@Param("orderNo") String orderNo,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("requestNo") String requestNo,
                        @Param("operator") String operator,
                        @Param("bizTime") LocalDateTime bizTime);
}
