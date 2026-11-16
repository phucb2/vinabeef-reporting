package com.vinabeef.reporting.kernel.model;

public record SyncResult(SyncStatus status, int attempts, String externalRef, String message) {
    public static SyncResult success(int attempts, String externalRef) {
        return new SyncResult(SyncStatus.SUCCESS, attempts, externalRef, "OK");
    }
    public static SyncResult failed(int attempts, String message) {
        return new SyncResult(SyncStatus.FAILED, attempts, null, message);
    }
    public boolean isSuccess() { return status == SyncStatus.SUCCESS; }
}
