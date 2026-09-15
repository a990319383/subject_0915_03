package com.evops.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 追溯日志基类（append-only）：流转留痕 / 反向记录 / 扣次记录。
 * 日志只插不改，因此没有 deleted / dataVersion。
 */
@Data
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private String requestNo;

    @TableField(fill = FieldFill.INSERT)
    private String operator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime bizTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
