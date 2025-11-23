package com.rigoberto.pr.Models;

/**
 * Representa el resultado de la ejecución de un método subscriber.
 * Permite al EventWorker saber si debe marcar el evento como SUCCESS o reintentar.
 */
public class EventExecutionResult {
    
    private final boolean success;
    private final Throwable error;
    private final String eventId;
    
    private EventExecutionResult(boolean success, Throwable error, String eventId) {
        this.success = success;
        this.error = error;
        this.eventId = eventId;
    }
    
    public static EventExecutionResult success(String eventId) {
        return new EventExecutionResult(true, null, eventId);
    }
    
    public static EventExecutionResult failure(String eventId, Throwable error) {
        return new EventExecutionResult(false, error, eventId);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public Throwable getError() {
        return error;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    @Override
    public String toString() {
        return "EventExecutionResult{" +
                "success=" + success +
                ", eventId='" + eventId + '\'' +
                ", error=" + (error != null ? error.getMessage() : "none") +
                '}';
    }
}
