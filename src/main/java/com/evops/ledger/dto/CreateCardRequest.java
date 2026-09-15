package com.evops.ledger.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
public class CreateCardRequest {

    /** 卡号，可空；为空时服务端生成。 */
    @Size(max = 32)
    private String cardNo;

    @NotBlank
    @Size(max = 32)
    private String storeCode;

    @Size(max = 64)
    private String holderName;

    @Size(max = 32)
    private String holderPhone;

    @NotNull
    @Positive
    private Integer totalTimes;
}
