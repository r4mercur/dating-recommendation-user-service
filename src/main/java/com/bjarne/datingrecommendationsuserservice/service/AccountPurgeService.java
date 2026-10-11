package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.entity.User;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.search.SearchIndexService;
import com.bjarne.datingrecommendationsuserservice.security.AccountStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes accounts whose deletion was requested longer than the grace period ago (GDPR).
 * The database removes everything that belongs to the user with the row (on delete cascade): preferences,
 * reactions, matches with their messages, blocks, songs, games and reports against them. Reports the user filed
 * stay without reporter. Afterwards the profile picture and the search document are removed.
 * Until the purge an admin can restore the account by setting it ACTIVE again.
 */
@Service
public class AccountPurgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountPurgeService.class);

    private final UserRepository userRepository;
    private final ProfilePictureStorageService profilePictureStorageService;
    private final SearchIndexService searchIndexService;
    private final AccountStatusService accountStatusService;
    private final TransactionTemplate transactionTemplate;
    private final Duration gracePeriod;

    public AccountPurgeService(UserRepository userRepository,
                               ProfilePictureStorageService profilePictureStorageService,
                               SearchIndexService searchIndexService,
                               AccountStatusService accountStatusService,
                               TransactionTemplate transactionTemplate,
                               @Value("${app.account-deletion.grace-period:P7D}") Duration gracePeriod) {
        this.userRepository = userRepository;
        this.profilePictureStorageService = profilePictureStorageService;
        this.searchIndexService = searchIndexService;
        this.accountStatusService = accountStatusService;
        this.transactionTemplate = transactionTemplate;
        this.gracePeriod = gracePeriod;
    }

    @Scheduled(cron = "${app.account-deletion.purge-cron:0 15 * * * *}")
    public void purgeScheduled() {
        purgeExpired();
    }

    /**
     * Deletes every account that is due. Each account is deleted in its own transaction, so one failure does not
     * keep the others. Returns the number of deleted accounts.
     */
    public int purgeExpired() {
        Instant cutoff = Instant.now().minus(gracePeriod);
        int purged = 0;
        for (User candidate : userRepository.findAllByStatusAndDeletionRequestedAtBefore(UserStatus.MARKED_FOR_DELETION, cutoff)) {
            try {
                purge(candidate.getReferenceId(), cutoff);
                purged++;
            } catch (RuntimeException e) {
                LOGGER.error("Failed to delete account {}", candidate.getReferenceId(), e);
            }
        }
        if (purged > 0) {
            LOGGER.info("Deleted {} accounts after their grace period", purged);
        }
        return purged;
    }

    private void purge(String referenceId, Instant cutoff) {
        // re-checked inside the transaction: an admin may have restored the account in the meantime
        String photoKey = transactionTemplate.execute(_ -> userRepository.findByReferenceId(referenceId)
                .filter(user -> user.getStatus() == UserStatus.MARKED_FOR_DELETION)
                .filter(user -> user.getDeletionRequestedAt() != null && user.getDeletionRequestedAt().isBefore(cutoff))
                .map(user -> {
                    String key = user.getPhotoKey();
                    userRepository.delete(user);
                    return key == null ? "" : key;
                })
                .orElse(null));
        if (photoKey == null) {
            return;
        }

        if (!photoKey.isEmpty()) {
            profilePictureStorageService.delete(photoKey);
        }
        searchIndexService.remove(referenceId);
        accountStatusService.forget(referenceId);
        LOGGER.info("Deleted account {}", referenceId);
    }
}
