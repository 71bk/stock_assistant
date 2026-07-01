package tw.bk.appocr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.apppersistence.entity.OcrJobEntity;

/** Publishes queue messages only after the surrounding database transaction commits. */
@Component
@RequiredArgsConstructor
class OcrQueuePublisher {
    private final OcrQueueService queueService;

    void enqueueAfterCommit(OcrJobEntity job) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            queueService.enqueue(job);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                queueService.enqueue(job);
            }
        });
    }
}
