package com.evops.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.common.BizException;
import com.evops.ledger.dto.CreateCardRequest;
import com.evops.ledger.entity.MemberCard;
import com.evops.ledger.mapper.MemberCardMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MemberCardService {

    private final MemberCardMapper cardMapper;

    public MemberCardService(MemberCardMapper cardMapper) {
        this.cardMapper = cardMapper;
    }

    public MemberCard create(CreateCardRequest req) {
        String cardNo = StringUtils.hasText(req.getCardNo()) ? req.getCardNo() : generateCardNo();
        Long exists = cardMapper.selectCount(new LambdaQueryWrapper<MemberCard>()
                .eq(MemberCard::getCardNo, cardNo));
        if (exists != null && exists > 0) {
            throw new BizException("会员卡已存在: " + cardNo);
        }
        MemberCard card = new MemberCard();
        card.setCardNo(cardNo);
        card.setStoreCode(req.getStoreCode());
        card.setHolderName(req.getHolderName());
        card.setHolderPhone(req.getHolderPhone());
        card.setTotalTimes(req.getTotalTimes());
        card.setRemainingTimes(req.getTotalTimes());
        card.setStatus("ACTIVE");
        cardMapper.insert(card);
        return card;
    }

    public MemberCard get(String cardNo) {
        MemberCard card = cardMapper.selectOne(new LambdaQueryWrapper<MemberCard>()
                .eq(MemberCard::getCardNo, cardNo));
        if (card == null) {
            throw new BizException("会员卡不存在或已删除: " + cardNo);
        }
        return card;
    }

    public List<MemberCard> list(String storeCode) {
        return cardMapper.selectList(new LambdaQueryWrapper<MemberCard>()
                .eq(StringUtils.hasText(storeCode), MemberCard::getStoreCode, storeCode)
                .orderByAsc(MemberCard::getCardNo));
    }

    private String generateCardNo() {
        return "MC" + System.currentTimeMillis()
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
