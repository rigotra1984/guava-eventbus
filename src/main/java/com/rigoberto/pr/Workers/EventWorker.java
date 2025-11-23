package com.rigoberto.pr.Workers;

import com.google.common.eventbus.EventBus;

import java.util.List;
import java.util.concurrent.*;

import com.rigoberto.pr.Models.StoredEvent;
import com.rigoberto.pr.Repositories.PostgreSQLEventRepository;

public class EventWorker {

    private final PostgreSQLEventRepository repo;
    private final EventBus eventBus;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private final int batchSize = 20;

    public EventWorker(PostgreSQLEventRepository repo, EventBus eventBus, int concurrency) {
        this.repo = repo;
        this.eventBus = eventBus;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.workers = Executors.newFixedThreadPool(concurrency);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<StoredEvent> events = repo.fetchPendingEvents(batchSize);
                for (var ev : events) {
                    workers.submit(() -> processEvent(ev));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void processEvent(StoredEvent ev) {
        try {
            // Aquí reconstruyes tu evento real. Ejemplo:
            Object realEvent = deserializeEvent(ev.getType(), ev.getPayload());

            eventBus.post(realEvent);

            repo.markAsSuccess(ev.getId());

        } catch (Exception e) {
            retryWithBackoff(ev);
        }
    }

    private void retryWithBackoff(StoredEvent ev) {
        try {
            int attempt = ev.getAttempts() + 1;

            if (attempt >= ev.getMaxAttempts()) {
                repo.markAsFailed(ev.getId(), attempt, 0);
                return;
            }

            long backoff = (long) Math.pow(2, attempt) * 1000L; // 2^n segundos

            repo.markAsFailed(ev.getId(), attempt, backoff);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Object deserializeEvent(String type, String payload) {
        // Usa Jackson o Gson para tu caso real
        return payload; // demo
    }
}
