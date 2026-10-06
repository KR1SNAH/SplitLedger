package com.splitledger.group;

/** A user's role within one group (separate from the app-wide USER/ADMIN role). */
public enum GroupRole {
    /** Can edit or delete the group, manage invites and members, and change roles. */
    OWNER,
    /** Can see the group and its members, and leave. */
    MEMBER
}
