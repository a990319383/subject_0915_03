package com.evops.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.ledger.entity.MemberCard;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface MemberCardMapper extends BaseMapper<MemberCard> {

    /**
     * 核销扣次：按读到的剩余次数做 CAS，剩余不足或并发漂移时返回 0。
     */
    @Update("UPDATE t_member_card SET remaining_times = remaining_times - 1, "
            + "data_version = data_version + 1, request_no = #{requestNo}, operator = #{operator}, "
            + "biz_time = #{bizTime}, update_time = CURRENT_TIMESTAMP "
            + "WHERE card_no = #{cardNo} AND remaining_times = #{expectRemaining} "
            + "AND status = 'ACTIVE' AND deleted = 0")
    int deductOnce(@Param("cardNo") String cardNo,
                   @Param("expectRemaining") int expectRemaining,
                   @Param("requestNo") String requestNo,
                   @Param("operator") String operator,
                   @Param("bizTime") LocalDateTime bizTime);

    /**
     * 作废回补：剩余次数 +1，不超过总次数。
     */
    @Update("UPDATE t_member_card SET remaining_times = remaining_times + 1, "
            + "data_version = data_version + 1, request_no = #{requestNo}, operator = #{operator}, "
            + "biz_time = #{bizTime}, update_time = CURRENT_TIMESTAMP "
            + "WHERE card_no = #{cardNo} AND remaining_times < total_times AND deleted = 0")
    int refundOnce(@Param("cardNo") String cardNo,
                   @Param("requestNo") String requestNo,
                   @Param("operator") String operator,
                   @Param("bizTime") LocalDateTime bizTime);
}
