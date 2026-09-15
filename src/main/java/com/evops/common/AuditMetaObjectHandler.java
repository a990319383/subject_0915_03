package com.evops.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充：任何经 MyBatis-Plus 的写入，
 * 请求号 / 操作人 / 业务时间 / 数据版本随 SQL 一起落库。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "requestNo", String.class, RequestContext.currentRequestNo());
        this.strictInsertFill(metaObject, "operator", String.class, RequestContext.currentOperator());
        this.strictInsertFill(metaObject, "bizTime", LocalDateTime.class, RequestContext.currentBizTime());
        this.strictInsertFill(metaObject, "dataVersion", Integer.class, 0);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新也是一次写入：强制刷新为当前请求的审计信息
        this.setFieldValByName("requestNo", RequestContext.currentRequestNo(), metaObject);
        this.setFieldValByName("operator", RequestContext.currentOperator(), metaObject);
        this.setFieldValByName("bizTime", RequestContext.currentBizTime(), metaObject);
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
