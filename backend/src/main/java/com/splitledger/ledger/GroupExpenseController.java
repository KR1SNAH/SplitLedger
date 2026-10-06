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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.splitledger.common.CurrentUser;
import com.splitledger.common.PageResponse;
import com.splitledger.ledger.dto.GroupExpenseRequest;
import com.splitledger.ledger.dto.GroupExpenseResponse;
import com.splitledger.ledger.dto.ShareResponse;
import com.splitledger.ledger.dto.SplitPreviewRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** Any member can view and add expenses; the service allows edits only by the expense's creator or an owner. */
@Tag(name = "Group expenses")
@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@PreAuthorize("@groupPermissions.isMember(#groupId, authentication)")
@RequiredArgsConstructor
public class GroupExpenseController {

    private final GroupExpenseService expenseService;

    @GetMapping
    @Operation(summary = "Group expenses, newest first")
    public PageResponse<GroupExpenseResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return expenseService.list(groupId, CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/{expenseId}")
    public GroupExpenseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID expenseId) {
        return expenseService.get(groupId, expenseId, CurrentUser.id(jwt));
    }

    @PostMapping("/preview")
    @Operation(summary = "Work out the shares for a split without saving it")
    public List<ShareResponse> preview(@PathVariable UUID groupId, @Valid @RequestBody SplitPreviewRequest request) {
        return expenseService.preview(groupId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupExpenseResponse create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @Valid @RequestBody GroupExpenseRequest request) {
        return expenseService.create(groupId, CurrentUser.id(jwt), request);
    }

    @PutMapping("/{expenseId}")
    @Operation(summary = "Edit an expense (its creator or an owner); requires the current version")
    public GroupExpenseResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody GroupExpenseRequest request) {
        return expenseService.update(groupId, expenseId, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense (its creator or an owner)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID expenseId) {
        expenseService.delete(groupId, expenseId, CurrentUser.id(jwt));
    }
}
