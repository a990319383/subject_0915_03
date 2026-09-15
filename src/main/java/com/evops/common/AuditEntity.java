package com.evops.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务表审计基类：请求号 / 操作人 / 业务时间 / 数据版本 / 删除标记。
 * 每次写入（含更新）都会刷新 requestNo / operator / bizTime。
 */
@Data
public class AuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String requestNo;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String operator;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime bizTime;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer dataVersion;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
