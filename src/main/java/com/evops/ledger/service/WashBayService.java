package com.evops.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.common.BizException;
import com.evops.ledger.dto.CreateBayRequest;
import com.evops.ledger.entity.WashBay;
import com.evops.ledger.mapper.WashBayMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class WashBayService {

    private final WashBayMapper bayMapper;

    public WashBayService(WashBayMapper bayMapper) {
        this.bayMapper = bayMapper;
    }

    public WashBay create(CreateBayRequest req) {
        Long exists = bayMapper.selectCount(new LambdaQueryWrapper<WashBay>()
                .eq(WashBay::getStoreCode, req.getStoreCode())
                .eq(WashBay::getBayCode, req.getBayCode()));
        if (exists != null && exists > 0) {
            throw new BizException("工位已存在: " + req.getStoreCode() + "/" + req.getBayCode());
        }
        WashBay bay = new WashBay();
        bay.setStoreCode(req.getStoreCode());
        bay.setBayCode(req.getBayCode());
        bay.setBayName(req.getBayName());
        bay.setBayType(StringUtils.hasText(req.getBayType()) ? req.getBayType() : "STANDARD");
        bay.setStatus("ENABLED");
        bayMapper.insert(bay);
        return bay;
    }

    public List<WashBay> list(String storeCode) {
        return bayMapper.selectList(new LambdaQueryWrapper<WashBay>()
                .eq(StringUtils.hasText(storeCode), WashBay::getStoreCode, storeCode)
                .orderByAsc(WashBay::getStoreCode)
                .orderByAsc(WashBay::getBayCode));
    }

    /** 删除只打标记。 */
    public void softDelete(String storeCode, String bayCode) {
        WashBay bay = bayMapper.selectOne(new LambdaQueryWrapper<WashBay>()
                .eq(WashBay::getStoreCode, storeCode)
                .eq(WashBay::getBayCode, bayCode));
        if (bay == null) {
            throw new BizException("工位不存在或已删除: " + storeCode + "/" + bayCode);
        }
        bayMapper.deleteById(bay.getId());
    }
}
