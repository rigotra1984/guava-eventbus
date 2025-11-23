package com.rigoberto.pr.Workers;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.rigoberto.pr.Repositories.PostgreSQLEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.Executors;

public class EventSystem {

    private final EventBus eventBus;
    private final PostgreSQLEventRepository repo;
    private final EventWorker worker;
    private final ObjectMapper objectMapper;
    private final RetryableSubscriberExceptionHandler exceptionHandler;

    public EventSystem(String jdbcUrl, String user, String pwd) throws Exception {
        this.objectMapper = new ObjectMapper();
        this.exceptionHandler = new RetryableSubscriberExceptionHandler();

        // Crear el EventBus real
        AsyncEventBus realEventBus = new AsyncEventBus(
                Executors.newCachedThreadPool(),
                exceptionHandler  // Usar el exception handler personalizado
        );

        // Envolver el EventBus para rastrear éxitos automáticamente
        this.eventBus = new TrackedEventBus(realEventBus, exceptionHandler);

        this.repo = new PostgreSQLEventRepository(jdbcUrl, user, pwd);

        this.worker = new EventWorker(repo, eventBus, exceptionHandler, 5);

        worker.start();
    }

    public void registerListener(Object listener) {
        eventBus.register(listener);
    }

    /**
     * Publica un evento serializándolo a JSON y almacenándolo en la base de datos.
     * 
     * @param event El evento a publicar
     * @throws Exception si hay error en la serialización o guardado
     */
    public void post(Object event) throws Exception {
        // Serializar usando Jackson
        String payload = objectMapper.writeValueAsString(event);
        String type = event.getClass().getName();
        repo.saveEvent(type, payload, 5);
    }
    
    /**
     * Obtiene el exception handler para acceso directo si es necesario.
     */
    public RetryableSubscriberExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }
}

