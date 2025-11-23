# Arquitectura del Sistema con @RetryableSubscribe

## 📐 Flujo de Ejecución

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         PUBLICACIÓN DE EVENTO                            │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │    EventSystem.post()  │
                    │  - Serializa a JSON    │
                    │  - Guarda en PostgreSQL│
                    └────────────┬───────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │   PostgreSQL (events)  │
                    │   status = 'PENDING'   │
                    └────────────┬───────────┘
                                 │
                      ┌──────────┴──────────┐
                      │   Polling (1 seg)   │
                      └──────────┬──────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                          PROCESAMIENTO                                     │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │
                                ▼
                   ┌────────────────────────┐
                   │   EventWorker          │
                   │  1. Deserializa evento │
                   │  2. Extrae eventId     │
                   │  3. Prepara tracking   │
                   └────────────┬───────────┘
                                │
                                ▼
                   ┌────────────────────────┐
                   │  TrackedEventBus.post()│
                   └────────────┬───────────┘
                                │
                                ▼
                   ┌────────────────────────┐
                   │    AsyncEventBus       │
                   │  (Guava EventBus)      │
                   └───────┬────────────────┘
                           │
          ┌────────────────┴────────────────┐
          │                                  │
          ▼                                  ▼
┌──────────────────────┐         ┌──────────────────────┐
│  Listener CON        │         │  Listener SIN        │
│  @RetryableSubscribe │         │  @RetryableSubscribe │
└──────────┬───────────┘         └──────────┬───────────┘
           │                                 │
           ├─ ÉXITO ────┐                   │
           │            │                   │
           ├─ FALLO ────┤                   │
           │            │                   │
           ▼            ▼                   ▼
┌────────────────────────────────────────────────────────┐
│     RetryableSubscriberExceptionHandler                │
│                                                         │
│  ┌──────────────────────────────────────────────────┐ │
│  │ Con @RetryableSubscribe:                         │ │
│  │  • ÉXITO → registerSuccess(eventId)             │ │
│  │  • FALLO → registerFailure(eventId, exception)  │ │
│  └──────────────────────────────────────────────────┘ │
│                                                         │
│  ┌──────────────────────────────────────────────────┐ │
│  │ Sin @RetryableSubscribe:                         │ │
│  │  • SIEMPRE → registerSuccess(eventId)           │ │
│  │  • (incluso si hay excepción)                    │ │
│  └──────────────────────────────────────────────────┘ │
└──────────────────────┬─────────────────────────────────┘
                       │
                       ▼
          ┌────────────────────────────┐
          │  TrackedEventBus.post()    │
          │  completa y señala latch   │
          └────────────┬───────────────┘
                       │
                       ▼
          ┌────────────────────────────┐
          │  EventWorker espera (5s)   │
          │  awaitExecution(eventId)   │
          └────────────┬───────────────┘
                       │
                       ▼
          ┌────────────────────────────┐
          │  Verificar resultado       │
          └────────────┬───────────────┘
                       │
          ┌────────────┴────────────┐
          │                         │
          ▼                         ▼
┌──────────────────┐    ┌──────────────────┐
│ result.isSuccess │    │ result.isFailure │
│ = true           │    │ = true           │
└──────┬───────────┘    └──────┬───────────┘
       │                       │
       ▼                       ▼
┌──────────────────┐    ┌──────────────────┐
│ markAsSuccess()  │    │ retryWithBackoff()│
│ en PostgreSQL    │    │ (backoff 2^n seg)│
└──────────────────┘    └──────────────────┘
```

## 🔧 Componentes Clave

### 1. TrackedEventBus
**Propósito**: Envuelve el AsyncEventBus para rastrear automáticamente el éxito.

**Funcionamiento**:
- Intercepta `post(event)`
- Después de publicar, llama a `registerSuccess(eventId)`
- Si hay excepción en el listener, el `ExceptionHandler` sobrescribe el resultado

### 2. RetryableSubscriberExceptionHandler
**Propósito**: Captura excepciones y registra resultados basándose en `@RetryableSubscribe`.

**Lógica**:
```java
if (método tiene @RetryableSubscribe) {
    if (excepción) {
        registerFailure(eventId, exception)  // → REINTENTO
    }
} else {
    // Sin @RetryableSubscribe
    registerSuccess(eventId)  // → SIEMPRE SUCCESS
}
```

### 3. EventWorker
**Propósito**: Procesa eventos y decide si marcar SUCCESS o reintentar.

**Proceso**:
1. Prepara tracking: `prepareExecution(eventId)`
2. Publica evento: `eventBus.post(event)`
3. Espera resultado: `awaitExecution(eventId, 5 segundos)`
4. Verifica resultado:
   - `isSuccess()` → `markAsSuccess()`
   - `isFailure()` → `retryWithBackoff()`

## 🎭 Escenarios de Uso

### Escenario A: Operación Crítica (Con @RetryableSubscribe)
```java
@Subscribe
@RetryableSubscribe
public void processPayment(PaymentEvent event) {
    paymentGateway.charge(event);  // Puede fallar
}
```
**Comportamiento**:
- Si `charge()` falla → excepción → `registerFailure()` → REINTENTO
- Si `charge()` tiene éxito → no hay excepción → `registerSuccess()` → SUCCESS

### Escenario B: Logging No Crítico (Sin @RetryableSubscribe)
```java
@Subscribe
public void logEvent(AnyEvent event) {
    logger.info("Event: {}", event);  // Puede fallar, no importa
}
```
**Comportamiento**:
- Si `logger.info()` falla → excepción → `registerSuccess()` → SUCCESS
- Si `logger.info()` tiene éxito → no hay excepción → `registerSuccess()` → SUCCESS
- **SIEMPRE SUCCESS** porque no tiene `@RetryableSubscribe`

## ⏱️ Sincronización con CountDownLatch

El sistema usa `CountDownLatch` para sincronizar la ejecución:

```java
// EventWorker
prepareExecution(eventId)           // Crea latch
eventBus.post(event)                 // Publica evento (asíncrono)
awaitExecution(eventId, 5 seconds)   // Espera a que termine

// TrackedEventBus / ExceptionHandler
registerSuccess(eventId)             // countDown() en latch
// o
registerFailure(eventId, exception)  // countDown() en latch
```

**Ventaja**: No hay polling ni sleeps, sincronización perfecta.

## ✅ Garantías

1. **Con @RetryableSubscribe**:
   - ✅ Solo marca SUCCESS si el método se ejecuta sin excepciones
   - ❌ Reintenta automáticamente si falla
   - 🔄 Backoff exponencial entre reintentos

2. **Sin @RetryableSubscribe**:
   - ✅ Siempre marca SUCCESS (comportamiento tradicional)
   - ✅ Compatible 100% con código existente
   - 🎯 Útil para operaciones no críticas

3. **Timeout de 5 segundos**:
   - ⏱️ Si el listener no responde en 5 segundos → asume SUCCESS
   - 🛡️ Previene bloqueos indefinidos

## 🔄 Reintentos con Backoff Exponencial

```
Intento 1: inmediato
Intento 2: espera 2^1 = 2 segundos
Intento 3: espera 2^2 = 4 segundos
Intento 4: espera 2^3 = 8 segundos
Intento 5: espera 2^4 = 16 segundos
Intento 6: marca como FAILED (máximo alcanzado)
```

Solo aplica para métodos con `@RetryableSubscribe`.
