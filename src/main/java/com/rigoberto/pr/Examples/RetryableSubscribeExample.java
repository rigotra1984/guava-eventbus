package com.rigoberto.pr.Examples;

import com.google.common.eventbus.Subscribe;
import com.rigoberto.pr.Annotations.RetryableSubscribe;
import com.rigoberto.pr.Workers.EventSystem;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ejemplo simple de cómo usar @RetryableSubscribe
 * 
 * Este ejemplo muestra:
 * 1. Un listener que SIEMPRE tiene éxito
 * 2. Un listener que FALLA las primeras N veces y luego tiene éxito
 * 3. Un listener SIN @RetryableSubscribe (comportamiento tradicional)
 */
public class RetryableSubscribeExample {

    public static void main(String[] args) throws Exception {
        // IMPORTANTE: Necesitas una base de datos PostgreSQL corriendo
        // Configura estos valores según tu entorno
        String jdbcUrl = "jdbc:postgresql://localhost:5432/eventdb";
        String user = "postgres";
        String password = "postgres";

        System.out.println("=== Iniciando EventSystem con @RetryableSubscribe ===\n");

        // Crear el EventSystem
        EventSystem eventSystem = new EventSystem(jdbcUrl, user, password);

        // Registrar listeners
        eventSystem.registerListener(new SuccessfulOrderListener());
        eventSystem.registerListener(new RetryablePaymentListener());
        eventSystem.registerListener(new SimpleLogListener());

        System.out.println("✓ Listeners registrados\n");

        // Publicar eventos de ejemplo
        System.out.println("--- Publicando eventos ---\n");

        // Evento 1: Será procesado exitosamente en el primer intento
        OrderCreatedEvent order = new OrderCreatedEvent("order-123", 100.50);
        eventSystem.post(order);
        System.out.println("✓ Evento OrderCreatedEvent publicado: " + order.getId());

        // Evento 2: Fallará 2 veces y luego tendrá éxito (reintento automático)
        PaymentEvent payment = new PaymentEvent("payment-456", 50.00);
        eventSystem.post(payment);
        System.out.println("✓ Evento PaymentEvent publicado: " + payment.getId());

        // Evento 3: Siempre se marca como SUCCESS (sin @RetryableSubscribe)
        LogEvent log = new LogEvent("log-789", "Something happened");
        eventSystem.post(log);
        System.out.println("✓ Evento LogEvent publicado: " + log.getId());

        System.out.println("\n--- Eventos publicados ---");
        System.out.println("Los eventos están siendo procesados en segundo plano...");
        System.out.println("Revisa los logs y la base de datos para ver los reintentos.\n");

        // Mantener el programa corriendo para que los eventos se procesen
        System.out.println("Presiona Ctrl+C para salir...");
        Thread.sleep(60000); // Esperar 1 minuto
    }

    // ========================================
    // EVENTOS DE EJEMPLO
    // ========================================

    public static class OrderCreatedEvent {
        private final String id;
        private final double amount;

        public OrderCreatedEvent(String id, double amount) {
            this.id = id;
            this.amount = amount;
        }

        public String getId() {
            return id;
        }

        public double getAmount() {
            return amount;
        }
    }

    public static class PaymentEvent {
        private final String id;
        private final double amount;

        public PaymentEvent(String id, double amount) {
            this.id = id;
            this.amount = amount;
        }

        public String getId() {
            return id;
        }

        public double getAmount() {
            return amount;
        }
    }

    public static class LogEvent {
        private final String id;
        private final String message;

        public LogEvent(String id, String message) {
            this.id = id;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public String getMessage() {
            return message;
        }
    }

    // ========================================
    // LISTENERS DE EJEMPLO
    // ========================================

    /**
     * Listener que SIEMPRE tiene éxito.
     * El evento se marca como SUCCESS en el primer intento.
     */
    public static class SuccessfulOrderListener {

        @Subscribe
        @RetryableSubscribe
        public void handleOrderCreated(OrderCreatedEvent event) {
            System.out.println("  [OrderListener] Procesando orden: " + event.getId() 
                             + " por $" + event.getAmount());
            
            // Simular procesamiento
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("  [OrderListener] ✓ Orden procesada exitosamente: " + event.getId());
            // No lanza excepción = éxito
        }
    }

    /**
     * Listener que FALLA las primeras 2 veces y luego tiene éxito.
     * Demuestra el comportamiento de reintento automático.
     */
    public static class RetryablePaymentListener {
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private static final int FAIL_TIMES = 2;

        @Subscribe
        @RetryableSubscribe
        public void handlePayment(PaymentEvent event) {
            int attempt = attemptCount.incrementAndGet();
            
            System.out.println("  [PaymentListener] Intento #" + attempt 
                             + " para procesar pago: " + event.getId());

            if (attempt <= FAIL_TIMES) {
                // Simular fallo (ej: servicio de pago no disponible)
                System.out.println("  [PaymentListener] ✗ Fallo simulado en intento #" + attempt);
                throw new RuntimeException("Payment gateway temporarily unavailable");
            }

            // Éxito después de N intentos
            System.out.println("  [PaymentListener] ✓ Pago procesado exitosamente en intento #" + attempt);
        }
    }

    /**
     * Listener SIN @RetryableSubscribe.
     * Se comporta como antes: siempre marca el evento como SUCCESS.
     */
    public static class SimpleLogListener {

        @Subscribe  // Sin @RetryableSubscribe
        public void handleLogEvent(LogEvent event) {
            System.out.println("  [LogListener] Registrando: " + event.getMessage());
            // Este listener no tiene @RetryableSubscribe
            // Por lo tanto, siempre se marca como SUCCESS automáticamente
        }
    }
}
