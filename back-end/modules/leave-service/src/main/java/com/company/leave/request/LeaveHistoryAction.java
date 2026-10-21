package com.company.leave.request;

import com.company.leave.request.LeaveModels.Status;

/** Stable codes shared by persisted history and the history API. */
public enum LeaveHistoryAction {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    ADJUSTMENT_SUBMITTED,
    ADJUSTMENT_APPROVED,
    ADJUSTMENT_REJECTED,
    ADJUSTMENT_CANCELLED;

    public static LeaveHistoryAction forDecision(Status status) {
        return switch (status) {
            case APPROVED -> APPROVED;
            case REJECTED -> REJECTED;
            case CANCELLED -> CANCELLED;
            case PENDING -> throw new IllegalArgumentException("Pending is not a leave decision");
        };
    }

    public static LeaveHistoryAction forAdjustmentDecision(Status status) {
        return switch (status) {
            case APPROVED -> ADJUSTMENT_APPROVED;
            case REJECTED -> ADJUSTMENT_REJECTED;
            case CANCELLED -> ADJUSTMENT_CANCELLED;
            case PENDING -> throw new IllegalArgumentException("Pending is not an adjustment decision");
        };
    }
}
