package com.rigoberto.pr.Annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación que marca un método subscriber como retryable.
 * 
 * Los métodos anotados con @RetryableSubscribe deben también tener @Subscribe de Guava.
 * Si el método lanza una excepción, el evento NO será marcado como SUCCESS y será reintentado.
 * Si el método se ejecuta sin excepción, el evento será marcado como SUCCESS.
 * 
 * Ejemplo de uso básico:
 * <pre>
 * {@code
 * @Subscribe
 * @RetryableSubscribe
 * public void handleUserCreated(UserCreatedEvent event) {
 *     // Si este método lanza excepción, el evento será reintentado
 *     // Timeout por defecto: 5 segundos
 *     userService.createUser(event);
 * }
 * }
 * </pre>
 * 
 * Ejemplo con timeout personalizado:
 * <pre>
 * {@code
 * @Subscribe
 * @RetryableSubscribe(timeoutSeconds = 10)
 * public void handleLongRunningTask(TaskEvent event) {
 *     // Este método tiene 10 segundos para completarse
 *     // Si no se completa en ese tiempo, será reintentado
 *     longRunningService.processTask(event);
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RetryableSubscribe {
    
    /**
     * Indica si se debe propagar la excepción después de registrarla.
     * Por defecto es false para no romper el flujo del EventBus.
     */
    boolean propagateException() default false;
    
    /**
     * Tiempo máximo de espera (en segundos) para que el método complete su ejecución.
     * Si el método no se completa en este tiempo, se considera que la ejecución no se completó
     * y el evento será reintentado.
     * Por defecto es 5 segundos.
     */
    long timeoutSeconds() default 5;
}
