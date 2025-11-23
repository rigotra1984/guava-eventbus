# Guía de Uso: @RetryableSubscribe

## 📌 Descripción

La anotación `@RetryableSubscribe` permite que los métodos subscriber (marcados con `@Subscribe` de Guava) manejen reintentos automáticos cuando fallan. 

**La solución es completamente genérica y maneja ambos escenarios:**
- ✅ **Con `@RetryableSubscribe`**: Si falla → se reintenta; si tiene éxito → se marca como SUCCESS
- ✅ **Sin `@RetryableSubscribe`**: Siempre se marca como SUCCESS (comportamiento tradicional)

**Características principales:**
- ✅ Si el método con `@RetryableSubscribe` se ejecuta sin errores → el evento se marca como **SUCCESS**
- ❌ Si el método con `@RetryableSubscribe` lanza una excepción → el evento se **reintenta** automáticamente
- 🔄 Sistema de backoff exponencial para reintentos
- 📊 Compatible 100% con listeners existentes sin `@RetryableSubscribe`
- 🎯 **Decisión por método**: cada listener puede elegir su estrategia

---

## 🎯 ¿Cómo funciona?

El sistema maneja **automáticamente ambos tipos de listeners**:

### Escenario 1: Con @RetryableSubscribe (reintentos automáticos)
```java
@Subscribe
@RetryableSubscribe
public void handlePayment(PaymentEvent event) {
    // Si este método falla, el evento se reintenta
    paymentService.process(event);
}
```
- ✅ **Éxito**: Método se ejecuta sin excepciones → evento marcado como SUCCESS
- ❌ **Fallo**: Método lanza excepción → evento reintentado con backoff exponencial

### Escenario 2: Sin @RetryableSubscribe (comportamiento tradicional)
```java
@Subscribe  // Sin @RetryableSubscribe
public void logEvent(AnyEvent event) {
    // Este listener SIEMPRE marca el evento como SUCCESS
    logger.info("Event: {}", event);
}
```
- ✅ Siempre se marca como SUCCESS, incluso si hay excepciones
- 🎯 Útil para logging, métricas, notificaciones no críticas

### Comparación Visual

| Característica | Con @RetryableSubscribe | Sin @RetryableSubscribe |
|----------------|------------------------|-------------------------|
| **Si tiene éxito** | ✅ Marca SUCCESS | ✅ Marca SUCCESS |
| **Si falla** | ❌ Reintenta con backoff | ✅ Marca SUCCESS (ignora error) |
| **Uso recomendado** | Operaciones críticas | Logging, métricas, notificaciones |
| **Idempotencia** | ⚠️ Requerida | No requerida |

### Comportamiento ANTES (sin este sistema)
```java
@Subscribe
public void handleEvent(UserCreatedEvent event) {
    // El evento se marcaba como SUCCESS inmediatamente
    // después de publicarse, sin esperar confirmación
    userService.createUser(event);
}
```
❌ **Problema:** No había forma de saber si el listener falló o tuvo éxito.

### Comportamiento AHORA (con este sistema)
```java
// Opción A: Con reintentos
@Subscribe
@RetryableSubscribe
public void handleEvent(UserCreatedEvent event) {
    // Solo se marca SUCCESS si se ejecuta sin excepciones
    userService.createUser(event);
}

// Opción B: Sin reintentos (comportamiento tradicional)
@Subscribe
public void handleEvent(UserCreatedEvent event) {
    // Siempre se marca SUCCESS (compatible con versión anterior)
    userService.createUser(event);
}
```
✅ **Solución:** Flexibilidad total. Cada método elige su estrategia.

---

## 📝 Ejemplos de Uso

### Ejemplo 1: Listener que siempre tiene éxito
```java
public class OrderListener {
    
    @Subscribe
    @RetryableSubscribe
    public void processOrder(OrderCreatedEvent event) {
        // Si todo sale bien, el evento se marca como SUCCESS
        orderService.processOrder(event.getOrderId());
        emailService.sendConfirmation(event.getCustomerEmail());
    }
}
```

### Ejemplo 2: Listener que puede fallar y reintentar
```java
public class PaymentListener {
    
    @Subscribe
    @RetryableSubscribe
    public void processPayment(PaymentEvent event) {
        // Si el servicio externo está caído, lanzará excepción
        // y el evento será reintentado automáticamente
        externalPaymentGateway.charge(event.getAmount(), event.getCardToken());
    }
}
```

### Ejemplo 3: Propagar excepciones (opcional)
```java
public class CriticalEventListener {
    
    @Subscribe
    @RetryableSubscribe(propagateException = true)
    public void handleCriticalEvent(CriticalEvent event) {
        // Si falla, además de reintentar, la excepción se propagará
        criticalService.processEvent(event);
    }
}
```

### Ejemplo 4: Listener sin @RetryableSubscribe (comportamiento tradicional)
```java
public class LogListener {
    
    @Subscribe  // Sin @RetryableSubscribe
    public void logEvent(AnyEvent event) {
        // Este listener NO reintenta en caso de error
        // El evento se marca como SUCCESS automáticamente
        logger.info("Event received: {}", event);
    }
}
```

---

## ⚙️ Configuración del Sistema

### 1. Crear el EventSystem
```java
EventSystem eventSystem = new EventSystem(jdbcUrl, user, password);
```

El `EventSystem` ahora automáticamente configura:
- Un `RetryableSubscriberExceptionHandler` personalizado
- Seguimiento de éxito/fallo de cada evento
- Reintentos con backoff exponencial

### 2. Registrar Listeners
```java
eventSystem.registerListener(new OrderListener());
eventSystem.registerListener(new PaymentListener());
```

### 3. Publicar Eventos
```java
OrderCreatedEvent event = new OrderCreatedEvent("order-123", "user@example.com");
eventSystem.post(event);
```

---

## 🔍 Requisitos para los Eventos

Para que el sistema pueda rastrear el éxito/fallo de cada evento, **es recomendable** que tus eventos tengan un método `getId()`:

```java
public class UserCreatedEvent {
    private final String id;
    private final String username;
    
    public UserCreatedEvent(String id, String username) {
        this.id = id;
        this.username = username;
    }
    
    public String getId() {  // ← Importante para tracking
        return id;
    }
    
    public String getUsername() {
        return username;
    }
}
```

Si tu evento no tiene `getId()`, el sistema usará automáticamente un identificador basado en el hashCode.

---

## 📊 Tabla de Estados de Eventos

| Estado | Descripción |
|--------|-------------|
| `PENDING` | Evento guardado, esperando ser procesado |
| `SUCCESS` | Evento procesado exitosamente (método no lanzó excepción) |
| `FAILED` | Evento falló después de agotar todos los reintentos |

---

## 🔄 Reintentos y Backoff

Cuando un método con `@RetryableSubscribe` lanza una excepción:

1. El evento **NO se marca como SUCCESS**
2. Se incrementa el contador de intentos
3. Se calcula un tiempo de espera usando **backoff exponencial**: `2^attempt * 1000ms`
4. El evento se reintenta hasta alcanzar el máximo de intentos (por defecto: 5)

**Ejemplo de tiempos de espera:**
- Intento 1: inmediato
- Intento 2: 2 segundos después
- Intento 3: 4 segundos después
- Intento 4: 8 segundos después
- Intento 5: 16 segundos después

---

## 🧪 Tests de Ejemplo

### Test 1: Éxito en el primer intento
```java
@Test
void testSuccessfulProcessing() throws Exception {
    SuccessfulListener listener = new SuccessfulListener();
    eventSystem.registerListener(listener);
    
    eventSystem.post(new TestEvent("test-123", "message"));
    
    // El evento debe marcarse como SUCCESS
    Thread.sleep(1000);
    // Verificar en base de datos que status = 'SUCCESS'
}
```

### Test 2: Fallo y reintento
```java
@Test
void testFailureAndRetry() throws Exception {
    // Listener que falla 2 veces y luego tiene éxito
    RetryableListener listener = new RetryableListener(failTimes = 2);
    eventSystem.registerListener(listener);
    
    eventSystem.post(new TestEvent("test-456", "retry me"));
    
    // El evento debe reintentar y eventualmente tener éxito
    Thread.sleep(10000);
    // Verificar que attempts >= 2 en base de datos
}
```

---

## ⚠️ Consideraciones Importantes

1. **Idempotencia**: Asegúrate de que tus listeners sean **idempotentes**, ya que pueden ser llamados múltiples veces si fallan.

2. **Excepciones Esperadas**: Si tu listener puede fallar de manera esperada (ej: servicio externo no disponible), usa `@RetryableSubscribe` para reintentar automáticamente.

3. **Excepciones Inesperadas**: Si una excepción indica un error de programación (bug), tal vez no quieras reintentar. En ese caso, no uses `@RetryableSubscribe`.

4. **Límite de Reintentos**: El número máximo de reintentos se configura al crear el evento (por defecto: 5). Después de agotarlos, el evento se marca como `FAILED`.

5. **Compatibilidad**: Los listeners sin `@RetryableSubscribe` siguen funcionando como antes (se marcan como SUCCESS automáticamente).

---

## 📦 Clases Principales

| Clase | Descripción |
|-------|-------------|
| `@RetryableSubscribe` | Anotación para métodos subscriber que deben reintentar |
| `EventExecutionResult` | Representa el resultado (éxito/fallo) de un método subscriber |
| `RetryableSubscriberExceptionHandler` | Captura excepciones y registra resultados de ejecución |
| `EventSystem` | Sistema principal que orquesta todo |
| `EventWorker` | Worker que procesa eventos y decide si marcar como SUCCESS o reintentar |

---

## 🚀 Migración desde Versión Anterior

Si tienes listeners existentes, la migración es sencilla:

### Antes:
```java
@Subscribe
public void handleEvent(MyEvent event) {
    myService.process(event);
}
```

### Después (con reintentos):
```java
@Subscribe
@RetryableSubscribe  // ← Agregar esta línea
public void handleEvent(MyEvent event) {
    myService.process(event);
}
```

**¡Eso es todo!** No necesitas cambiar ninguna otra parte de tu código.

---

## 📚 Recursos Adicionales

- Ver tests completos en: `RetryableSubscribeTest.java`
- Código de ejemplo en: `EventSystemIntegrationTest.java`
- Implementación de `@RetryableSubscribe`: `com.rigoberto.pr.Annotations.RetryableSubscribe`

---

## ❓ FAQ

**P: ¿Qué pasa si mi evento no tiene un método `getId()`?**  
R: El sistema usará automáticamente un identificador basado en el hashCode del evento.

**P: ¿Puedo mezclar listeners con y sin @RetryableSubscribe?**  
R: Sí, ambos tipos de listeners pueden coexistir sin problemas.

**P: ¿Cuánto tiempo espera entre reintentos?**  
R: Usa backoff exponencial: 2 segundos, 4 segundos, 8 segundos, etc.

**P: ¿Qué pasa si agoto todos los reintentos?**  
R: El evento se marca como `FAILED` y no se vuelve a procesar.

**P: ¿Puedo cambiar el número máximo de reintentos?**  
R: Actualmente se configura al guardar el evento (por defecto: 5). Podrías extender `@RetryableSubscribe` para incluir este parámetro.
