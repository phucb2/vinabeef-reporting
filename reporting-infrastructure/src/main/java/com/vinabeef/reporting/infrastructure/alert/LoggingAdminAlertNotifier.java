package com.vinabeef.reporting.infrastructure.alert;

import com.vinabeef.reporting.kernel.port.AdminAlertNotifier;

/** Default notifier: log at ERROR level (picked up by log-based alerting). Replace with e-mail/Slack adapter. */
public class LoggingAdminAlertNotifier implements AdminAlertNotifier {

    private static final System.Logger LOG = System.getLogger(LoggingAdminAlertNotifier.class.getName());

    @Override
    public void alert(String subject, String detail) {
        LOG.log(System.Logger.Level.ERROR, "[ADMIN ALERT] {0} — {1}", subject, detail);
    }
}
