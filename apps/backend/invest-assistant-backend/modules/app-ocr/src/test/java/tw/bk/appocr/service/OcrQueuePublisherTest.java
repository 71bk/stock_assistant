package tw.bk.appocr.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tw.bk.appocr.queue.OcrQueueService;
import tw.bk.apppersistence.entity.OcrJobEntity;

@ExtendWith(MockitoExtension.class)
class OcrQueuePublisherTest {
    @Mock
    private OcrQueueService queueService;

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void enqueueAfterCommit_shouldDelayPublishingUntilCommit() {
        OcrJobEntity job = new OcrJobEntity();
        OcrQueuePublisher publisher = new OcrQueuePublisher(queueService);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        publisher.enqueueAfterCommit(job);

        verify(queueService, never()).enqueue(job);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(queueService).enqueue(job);
    }

    @Test
    void enqueueAfterCommit_shouldPublishImmediatelyWithoutTransaction() {
        OcrJobEntity job = new OcrJobEntity();
        OcrQueuePublisher publisher = new OcrQueuePublisher(queueService);

        publisher.enqueueAfterCommit(job);

        verify(queueService).enqueue(job);
    }
}
