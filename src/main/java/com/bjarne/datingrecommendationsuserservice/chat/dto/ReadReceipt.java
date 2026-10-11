package com.bjarne.datingrecommendationsuserservice.chat.dto;

import java.time.Instant;

/**
 * Pushed on /user/queue/read-receipts: everything sent to the reader in this match up to readAt is read.
 */
public record ReadReceipt(String matchReferenceId, String readerReferenceId, Instant readAt) {
}
