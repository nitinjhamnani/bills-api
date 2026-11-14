package com.b2bfintech.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One info line for a change to money or a bill. Reads stay on the request log. */
final class MoneyActionLog {

    private static final Logger log = LoggerFactory.getLogger(MoneyActionLog.class);

    private MoneyActionLog() {
    }

    static void record(String action, String actor, String recordId) {
        log.info("action={} actor={} record={}", action, actor == null || actor.isBlank() ? "-" : actor, recordId);
    }
}
