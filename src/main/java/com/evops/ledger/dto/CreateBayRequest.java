package com.evops.ledger.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class CreateBayRequest {

    @NotBlank
    @Size(max = 32)
    private String storeCode;

    @NotBlank
    @Size(max = 32)
    private String bayCode;

    @NotBlank
    @Size(max = 64)
    private String bayName;

    @Size(max = 16)
    private String bayType;
}
