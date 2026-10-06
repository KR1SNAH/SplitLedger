package com.splitledger.group.dto;

import com.splitledger.group.GroupRole;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull GroupRole role) {
}
