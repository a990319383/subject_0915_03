package com.evops.ledger.controller;

import com.evops.common.ApiResponse;
import com.evops.ledger.dto.CreateCardRequest;
import com.evops.ledger.entity.MemberCard;
import com.evops.ledger.service.MemberCardService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wash/cards")
public class MemberCardController {

    private final MemberCardService cardService;

    public MemberCardController(MemberCardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping
    public ApiResponse<MemberCard> create(@Validated @RequestBody CreateCardRequest req) {
        return ApiResponse.ok(cardService.create(req));
    }

    @GetMapping("/{cardNo}")
    public ApiResponse<MemberCard> get(@PathVariable String cardNo) {
        return ApiResponse.ok(cardService.get(cardNo));
    }

    @GetMapping
    public ApiResponse<List<MemberCard>> list(@RequestParam(required = false) String storeCode) {
        return ApiResponse.ok(cardService.list(storeCode));
    }
}
