package com.rigoberto.pr.Workers;

import com.google.common.eventbus.EventBus;
import java.lang.reflect.Method;

/**
 * Wrapper para EventBus que rastrea automáticamente el éxito de eventos procesados.
 * 
 * Este wrapper intercepta el método post() y, después de publicar el evento,
 * registra automáticamente el éxito si no se lanzaron excepciones.
 */
public class TrackedEventBus extends EventBus {
    
    private final EventBus delegate;
    private final RetryableSubscriberExceptionHandler exceptionHandler;
    
    public TrackedEventBus(EventBus delegate, RetryableSubscriberExceptionHandler exceptionHandler) {
        this.delegate = delegate;
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override
    public void register(Object listener) {
        delegate.register(listener);
        exceptionHandler.registerListener(listener);
    }
    
    @Override
    public void unregister(Object listener) {
        delegate.unregister(listener);
    }
    
    @Override
    public void post(Object event) {
        String eventId = extractEventId(event);
        
        try {
            // Publicar el evento en el EventBus real
            delegate.post(event);
            
            // Si llegamos aquí y no hubo excepción, registrar éxito
            // NOTA: Esto no significa que el listener no haya fallado,
            // solo que el post() se completó. El exception handler
            // capturará las excepciones reales de los listeners.
            exceptionHandler.registerSuccess(eventId);
            
        } catch (Exception e) {
            // Si hubo excepción durante el post, no registrar éxito
            throw e;
        }
    }
    
    /**
     * Extrae el ID del evento para tracking.
     */
    private String extractEventId(Object event) {
        try {
            Method getIdMethod = event.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(event);
            return id != null ? id.toString() : event.getClass().getSimpleName() + "-" + event.hashCode();
        } catch (Exception e) {
            return event.getClass().getSimpleName() + "-" + event.hashCode();
        }
    }
}
