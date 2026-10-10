package org.voxelhorizons.economy.api;

import java.util.UUID;

public final class EconomyReceipt {
    public enum Status { APPLIED, ALREADY_APPLIED, INSUFFICIENT_FUNDS, BALANCE_LIMIT }
    private final UUID id;
    private final Status status;
    public EconomyReceipt(UUID id, Status status) {
        this.id = java.util.Objects.requireNonNull(id, "id");
        this.status = java.util.Objects.requireNonNull(status, "status");
    }
    public UUID id() { return id; }
    public Status status() { return status; }
    public boolean successful() { return status == Status.APPLIED || status == Status.ALREADY_APPLIED; }
}
