package com.rigoberto.pr.Workers;

import com.google.common.eventbus.EventBus;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import com.rigoberto.pr.Models.StoredEvent;
import com.rigoberto.pr.Repositories.PostgreSQLEventRepository;

public class EventWorker {

    private final PostgreSQLEventRepository repo;
    private final EventBus eventBus;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private final int batchSize = 20;
    private final ObjectMapper objectMapper;

    public EventWorker(PostgreSQLEventRepository repo, EventBus eventBus, int concurrency) {
        this.repo = repo;
        this.eventBus = eventBus;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.workers = Executors.newFixedThreadPool(concurrency);
        this.objectMapper = new ObjectMapper();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<StoredEvent> events = repo.fetchPendingEvents(batchSize);
                for (StoredEvent ev : events) {
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

    /**
     * Deserializa el payload JSON a un objeto basándose en el tipo.
     * 
     * @param type El nombre completo de la clase del evento (ej: "com.example.UserCreatedEvent")
     * @param payload El JSON string del evento
     * @return El objeto deserializado
     * @throws RuntimeException si hay error en la deserialización
     */
    private Object deserializeEvent(String type, String payload) {
        try {
            // Intenta cargar la clase del evento dinámicamente
            Class<?> eventClass = Class.forName(type);
            return objectMapper.readValue(payload, eventClass);
        } catch (ClassNotFoundException e) {
            // Si no existe la clase, deserializa como Map genérico
            System.err.println("Clase no encontrada: " + type + ". Usando Map genérico.");
            try {
                return objectMapper.readValue(payload, Map.class);
            } catch (Exception ex) {
                throw new RuntimeException("Error al deserializar como Map: " + payload, ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al deserializar evento de tipo " + type + ": " + payload, e);
        }
    }
}
