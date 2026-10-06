package com.splitledger.ledger;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.splitledger.common.CurrentUser;
import com.splitledger.ledger.dto.MyBalances;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Balances & settlements")
@RestController
@RequiredArgsConstructor
public class MyBalancesController {

    private final BalanceService balanceService;

    @GetMapping("/api/balances")
    @Operation(summary = "What you owe and are owed in each of your groups, with totals per currency")
    public MyBalances myBalances(@AuthenticationPrincipal Jwt jwt) {
        return balanceService.myBalances(CurrentUser.id(jwt));
    }
}
