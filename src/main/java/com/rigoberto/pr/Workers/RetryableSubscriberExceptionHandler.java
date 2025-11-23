package com.rigoberto.pr.Workers;

import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import com.rigoberto.pr.Annotations.RetryableSubscribe;
import com.rigoberto.pr.Models.EventExecutionResult;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manejador de excepciones personalizado para el EventBus que detecta métodos
 * anotados con @RetryableSubscribe y registra los resultados de ejecución.
 * 
 * Este manejador permite al EventWorker saber si un evento fue procesado exitosamente
 * o si debe ser reintentado.
 */
public class RetryableSubscriberExceptionHandler implements SubscriberExceptionHandler {
    
    private final Map<String, EventExecutionResult> executionResults = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> executionLatches = new ConcurrentHashMap<>();
    
    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
        Method subscriberMethod = context.getSubscriberMethod();
        Object event = context.getEvent();
        String eventId = extractEventId(event);
        
        // Verificar si el método tiene @RetryableSubscribe
        if (subscriberMethod.isAnnotationPresent(RetryableSubscribe.class)) {
            // Registrar fallo para métodos con @RetryableSubscribe
            EventExecutionResult failureResult = EventExecutionResult.failure(eventId, exception);
            executionResults.put(eventId, failureResult);
            
            System.err.println("Event processing failed for event " + eventId + 
                             " in method " + subscriberMethod.getName() + 
                             ": " + exception.getMessage());
            
            // Verificar si debe propagar la excepción
            RetryableSubscribe annotation = subscriberMethod.getAnnotation(RetryableSubscribe.class);
            if (annotation.propagateException()) {
                throw new RuntimeException("Event processing failed", exception);
            }
        } else {
            // Sin @RetryableSubscribe: registrar como éxito (comportamiento tradicional)
            // Los listeners sin @RetryableSubscribe siempre se consideran exitosos
            EventExecutionResult successResult = EventExecutionResult.success(eventId);
            executionResults.put(eventId, successResult);
            
            System.err.println("Exception in non-retryable subscriber method " + 
                             subscriberMethod.getName() + ": " + exception.getMessage());
        }
        
        // Señalar que la ejecución ha terminado
        CountDownLatch latch = executionLatches.get(eventId);
        if (latch != null) {
            latch.countDown();
        }
    }
    
    /**
     * Registra un evento como procesado exitosamente.
     * Este método debe ser llamado ANTES de publicar el evento para preparar el tracking.
     */
    public void prepareExecution(String eventId) {
        executionLatches.put(eventId, new CountDownLatch(1));
    }
    
    /**
     * Registra manualmente un evento como procesado exitosamente.
     * Esto se usa cuando el método se ejecuta sin lanzar excepciones.
     */
    public void registerSuccess(String eventId) {
        EventExecutionResult successResult = EventExecutionResult.success(eventId);
        executionResults.put(eventId, successResult);
        
        // Señalar que la ejecución ha terminado
        CountDownLatch latch = executionLatches.get(eventId);
        if (latch != null) {
            latch.countDown();
        }
    }
    
    /**
     * Espera a que un evento termine de procesarse (éxito o fallo).
     * 
     * @param eventId ID del evento
     * @param timeout Tiempo máximo de espera
     * @param unit Unidad de tiempo
     * @return true si terminó, false si se agotó el timeout
     */
    public boolean awaitExecution(String eventId, long timeout, TimeUnit unit) {
        CountDownLatch latch = executionLatches.get(eventId);
        if (latch == null) {
            return false;
        }
        
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Verifica si un evento fue procesado exitosamente.
     */
    public EventExecutionResult getExecutionResult(String eventId) {
        return executionResults.get(eventId);
    }
    
    /**
     * Limpia el resultado de un evento después de procesarlo.
     */
    public void clearResult(String eventId) {
        executionResults.remove(eventId);
        executionLatches.remove(eventId);
    }
    
    public Map<String, EventExecutionResult> getAllResults() {
        return executionResults;
    }
    
    /**
     * Intenta extraer un ID único del evento para rastrear su ejecución.
     */
    private String extractEventId(Object event) {
        try {
            // Intentar obtener getId()
            Method getIdMethod = event.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(event);
            return id != null ? id.toString() : event.getClass().getSimpleName() + "-" + event.hashCode();
        } catch (Exception e) {
            // Si no hay getId(), usar hashCode
            return event.getClass().getSimpleName() + "-" + event.hashCode();
        }
    }
}
