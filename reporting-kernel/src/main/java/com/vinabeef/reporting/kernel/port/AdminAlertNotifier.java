package com.vinabeef.reporting.kernel.port;

public interface AdminAlertNotifier {
    void alert(String subject, String detail);
}
