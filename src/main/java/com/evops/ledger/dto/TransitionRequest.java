package com.evops.ledger.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 状态流转请求：action ∈ START / FINISH / REDEEM / VOID。
 */
@Data
public class TransitionRequest {

    @NotBlank
    private String action;

    @Size(max = 255)
    private String reason;
}
