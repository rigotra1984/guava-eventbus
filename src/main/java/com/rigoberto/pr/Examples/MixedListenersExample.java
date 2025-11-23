package com.rigoberto.pr.Examples;

import com.google.common.eventbus.Subscribe;
import com.rigoberto.pr.Annotations.RetryableSubscribe;

/**
 * Ejemplo que demuestra ambos tipos de listeners trabajando juntos.
 * 
 * Este ejemplo muestra cómo un mismo EventSystem puede manejar:
 * 1. Listeners con @RetryableSubscribe (reintentos automáticos)
 * 2. Listeners sin @RetryableSubscribe (siempre SUCCESS)
 */
public class MixedListenersExample {

    /**
     * Evento de ejemplo
     */
    public static class OrderEvent {
        private final String id;
        private final double amount;

        public OrderEvent(String id, double amount) {
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

    /**
     * Listener CRÍTICO con @RetryableSubscribe
     * Si falla, el evento se reintenta automáticamente
     */
    public static class PaymentProcessor {

        @Subscribe
        @RetryableSubscribe  // ← Reintenta si falla
        public void processPayment(OrderEvent event) {
            System.out.println("[PaymentProcessor] Procesando pago de orden: " + event.getId());
            
            // Simular fallo ocasional
            if (Math.random() < 0.3) {
                throw new RuntimeException("Payment gateway timeout");
            }
            
            System.out.println("[PaymentProcessor] ✓ Pago procesado exitosamente");
        }
    }

    /**
     * Listener NO CRÍTICO sin @RetryableSubscribe
     * Siempre se marca como SUCCESS, incluso si falla
     */
    public static class ActivityLogger {

        @Subscribe  // Sin @RetryableSubscribe
        public void logActivity(OrderEvent event) {
            System.out.println("[ActivityLogger] Registrando actividad: " + event.getId());
            
            // Simular fallo ocasional
            if (Math.random() < 0.3) {
                throw new RuntimeException("Logging service unavailable");
            }
            
            System.out.println("[ActivityLogger] ✓ Actividad registrada");
        }
    }

    /**
     * Listener de MÉTRICAS sin @RetryableSubscribe
     * Siempre SUCCESS porque las métricas no son críticas
     */
    public static class MetricsCollector {

        @Subscribe  // Sin @RetryableSubscribe
        public void collectMetrics(OrderEvent event) {
            System.out.println("[MetricsCollector] Recolectando métricas: orden " + event.getId() + 
                             ", monto $" + event.getAmount());
            
            // Este listener SIEMPRE se marca como SUCCESS
            // incluso si falla, porque las métricas no son críticas
        }
    }

    /**
     * Listener de INVENTARIO con @RetryableSubscribe
     * Operación crítica que debe reintentar si falla
     */
    public static class InventoryManager {

        @Subscribe
        @RetryableSubscribe  // ← Reintenta si falla
        public void updateInventory(OrderEvent event) {
            System.out.println("[InventoryManager] Actualizando inventario para: " + event.getId());
            
            // Simular fallo ocasional
            if (Math.random() < 0.2) {
                throw new RuntimeException("Inventory database connection failed");
            }
            
            System.out.println("[InventoryManager] ✓ Inventario actualizado");
        }
    }

    /**
     * Listener de EMAIL sin @RetryableSubscribe
     * No es crítico si falla el envío de email
     */
    public static class EmailNotifier {

        @Subscribe  // Sin @RetryableSubscribe
        public void sendEmail(OrderEvent event) {
            System.out.println("[EmailNotifier] Enviando email de confirmación: " + event.getId());
            
            // Si falla, no pasa nada. No es crítico.
            // El evento se marca como SUCCESS de todas formas
        }
    }

    /**
     * Resumen del comportamiento:
     * 
     * CRÍTICOS (con @RetryableSubscribe):
     * - PaymentProcessor: Si falla → REINTENTO
     * - InventoryManager: Si falla → REINTENTO
     * 
     * NO CRÍTICOS (sin @RetryableSubscribe):
     * - ActivityLogger: Si falla → SUCCESS (no importa)
     * - MetricsCollector: Siempre → SUCCESS
     * - EmailNotifier: Si falla → SUCCESS (no importa)
     * 
     * VENTAJA: El evento solo se marca como SUCCESS si TODOS los listeners
     * críticos tienen éxito. Los no críticos no afectan el estado del evento.
     */
}
