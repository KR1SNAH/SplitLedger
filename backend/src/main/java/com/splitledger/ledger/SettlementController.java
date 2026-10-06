package com.splitledger.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.splitledger.common.CurrentUser;
import com.splitledger.ledger.dto.GroupBalances;
import com.splitledger.ledger.dto.SettlementRequest;
import com.splitledger.ledger.dto.SettlementResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Balances & settlements")
@RestController
@RequestMapping("/api/groups/{groupId}")
@PreAuthorize("@groupPermissions.isMember(#groupId, authentication)")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final BalanceService balanceService;

    @GetMapping("/balances")
    @Operation(summary = "Each member's balance, plus the fewest payments that settle everyone up")
    public GroupBalances balances(@PathVariable UUID groupId) {
        return balanceService.balances(groupId);
    }

    @GetMapping("/settlements")
    @Operation(summary = "Recorded payments, newest first")
    public List<SettlementResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return settlementService.list(groupId, CurrentUser.id(jwt));
    }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a payment you made or received (owners can record any)")
    public SettlementResponse create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @Valid @RequestBody SettlementRequest request) {
        return settlementService.create(groupId, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/settlements/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a payment (either person involved, or an owner)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID settlementId) {
        settlementService.delete(groupId, settlementId, CurrentUser.id(jwt));
    }
}
