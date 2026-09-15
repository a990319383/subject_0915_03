package com.evops.ledger.controller;

import com.evops.common.ApiResponse;
import com.evops.ledger.dto.CreateBayRequest;
import com.evops.ledger.entity.WashBay;
import com.evops.ledger.service.WashBayService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wash/bays")
public class WashBayController {

    private final WashBayService bayService;

    public WashBayController(WashBayService bayService) {
        this.bayService = bayService;
    }

    @PostMapping
    public ApiResponse<WashBay> create(@Validated @RequestBody CreateBayRequest req) {
        return ApiResponse.ok(bayService.create(req));
    }

    @GetMapping
    public ApiResponse<List<WashBay>> list(@RequestParam(required = false) String storeCode) {
        return ApiResponse.ok(bayService.list(storeCode));
    }

    @DeleteMapping("/{storeCode}/{bayCode}")
    public ApiResponse<Void> softDelete(@PathVariable String storeCode, @PathVariable String bayCode) {
        bayService.softDelete(storeCode, bayCode);
        return ApiResponse.ok(null);
    }
}
