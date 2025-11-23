# EventBus - Sistema de Eventos Persistentes

[![Maven Central](https://img.shields.io/maven-central/v/com.rigoberto.pr/eventbus.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.rigoberto.pr%22%20AND%20a:%22eventbus%22)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

Sistema de eventos asíncronos con persistencia en PostgreSQL, retry automático con backoff exponencial y procesamiento concurrente. Perfecto para arquitecturas event-driven que requieren garantías de entrega y procesamiento robusto de eventos.

## 📋 Tabla de Contenidos

- [Características](#-características)
- [Instalación](#-instalación)
- [Inicio Rápido](#-inicio-rápido)
- [Arquitectura](#-arquitectura)
- [Componentes](#-componentes)
- [Uso Detallado](#-uso-detallado)
- [Base de Datos](#-base-de-datos)
- [Configuración](#-configuración)
- [Tests](#-tests)
- [Build y Deployment](#-build-y-deployment)
- [Estructura del Proyecto](#-estructura-del-proyecto)
- [Dependencias](#-dependencias)
- [Roadmap](#-roadmap)
- [Contribuir](#-contribuir)
- [Licencia](#-licencia)

## ✨ Características

- **🚀 EventBus Asíncrono**: Basado en Google Guava EventBus para publicación/suscripción desacoplada
- **💾 Persistencia Garantizada**: Almacenamiento de eventos en PostgreSQL antes de procesarlos
- **🔄 Retry Automático**: Reintentos con backoff exponencial (2^n * 1000ms) en caso de fallos
- **🎯 @RetryableSubscribe**: Anotación personalizada para controlar éxito/fallo de listeners
  - ⏱️ **Timeout configurable por método** con el parámetro `timeoutSeconds`
  - 🛡️ **Detección de servidor detenido**: reintenta eventos incompletos
  - 📊 **Compatible** con listeners existentes sin la anotación
- **⚡ Procesamiento Concurrente**: Pool configurable de workers para procesar múltiples eventos en paralelo
- **🎯 Serialización JSON**: Eventos serializados con Jackson para máxima flexibilidad
- **🧪 Tests Completos**: Suite de tests con Testcontainers para pruebas end-to-end
- **📊 Estados de Eventos**: Sistema de estados (PENDING → SUCCESS/FAILED) con tracking de intentos
- **🔧 Fácil Integración**: API simple con 3 métodos principales

## 📦 Instalación

### Maven

Agrega la dependencia a tu `pom.xml`:

```xml
<dependency>
    <groupId>com.rigoberto.pr</groupId>
    <artifactId>eventbus</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.rigoberto.pr:eventbus:1.0.0'
```

## 🚀 Inicio Rápido

### 1. Configurar PostgreSQL

```bash
docker run -d --name postgres \
  -e POSTGRES_DB=eventdb \
  -e POSTGRES_USER=eventuser \
  -e POSTGRES_PASSWORD=eventpass \
  -p 5432:5432 \
  postgres:15-alpine
```

### 2. Inicializar el sistema

```java
import com.rigoberto.pr.Workers.EventSystem;

EventSystem eventSystem = new EventSystem(
    "jdbc:postgresql://localhost:5432/eventdb",
    "eventuser",
    "eventpass"
);
```

### 3. Crear y registrar un listener

**Opción A: Con reintentos automáticos (recomendado para operaciones críticas)**
```java
import com.google.common.eventbus.Subscribe;
import com.rigoberto.pr.Annotations.RetryableSubscribe;

public class PaymentListener {
    @Subscribe
    @RetryableSubscribe(timeoutSeconds = 10)  // Timeout de 10 segundos
    public void handlePayment(PaymentEvent event) {
        // Si este método falla o no completa en 10 segundos, 
        // el evento se reintenta automáticamente
        paymentService.processPayment(event);
    }
}
```

**Opción B: Sin reintentos (recomendado para operaciones no críticas)**
```java
import com.google.common.eventbus.Subscribe;

public class LoggingListener {
    @Subscribe  // Sin @RetryableSubscribe
    public void logEvent(UserCreatedEvent event) {
        // Siempre se marca como SUCCESS, incluso si falla
        logger.info("Usuario creado: " + event.getUserId());
    }
}
```

**Registrar los listeners:**
```java
eventSystem.registerListener(new PaymentListener());
eventSystem.registerListener(new LoggingListener());
```

> 📚 **Documentación completa**: Ver [RETRYABLE_SUBSCRIBE_GUIDE.md](RETRYABLE_SUBSCRIBE_GUIDE.md) para entender a fondo cómo funciona `@RetryableSubscribe`.

### 4. Publicar eventos

```java
UserCreatedEvent event = new UserCreatedEvent("user123", "john@example.com");
eventSystem.post(event);
```

El evento se serializa automáticamente a JSON, se persiste en PostgreSQL y se procesa asíncronamente.

## 🏗️ Arquitectura

```
┌─────────────────────┐
│   Application       │
│   (Your Code)       │
└──────────┬──────────┘
           │
           │ post(event)
           ▼
┌─────────────────────┐
│    EventSystem      │  ← Punto de entrada principal
│  - registerListener │
│  - post(event)      │
└──────┬──────────┬───┘
       │          │
       │          └──────────────────┐
       │                             │
       ▼                             ▼
┌─────────────────┐    ┌─────────────────────────┐
│   EventBus      │    │ PostgreSQLEventRepo     │
│   (Guava)       │    │  - saveEvent()          │
│  - Async        │    │  - fetchPending()       │
│  - Concurrent   │    │  - markAsSuccess()      │
└────────┬────────┘    │  - markAsFailed()       │
         │             └───────────┬─────────────┘
         │                         │
         │                         │ JDBC
         │                         ▼
         │             ┌──────────────────────────┐
         │             │      PostgreSQL          │
         │             │    events table          │
         │             │  - id, type, payload     │
         │             │  - status, attempts      │
         │             │  - next_attempt_at       │
         │             └───────────▲──────────────┘
         │                         │
         │                         │ polling (1s)
         │                         │
         │             ┌───────────┴──────────────┐
         └─────────────►    EventWorker           │
                       │  - Polling loop          │
                       │  - Concurrent processing │
                       │  - Retry con backoff     │
                       └──────────────────────────┘
```

### Flujo de Trabajo

1. **Publicación**: Tu aplicación llama a `eventSystem.post(event)`
2. **Serialización**: El evento se serializa a JSON usando Jackson
3. **Persistencia**: Se guarda en PostgreSQL con estado `PENDING`
4. **Polling**: EventWorker hace polling cada segundo buscando eventos pendientes
5. **Procesamiento**: Los eventos se procesan concurrentemente en un pool de threads
6. **Delivery**: Se deserializa el evento y se publica en el EventBus de Guava
7. **Listeners**: Los listeners registrados reciben el evento de forma asíncrona
8. **Resultado**:
   - ✅ **Éxito**: Se marca como `SUCCESS`
   - ❌ **Fallo**: Se incrementa `attempts` y se programa retry con backoff exponencial

## 🔧 Componentes

### EventSystem

**Descripción**: Punto de entrada principal que orquesta todo el sistema.

**Responsabilidades**:
- Inicializar el EventBus asíncrono
- Crear la conexión con PostgreSQL
- Iniciar el EventWorker
- Proporcionar la API pública

**API**:
```java
public class EventSystem {
    // Constructor: inicializa todo el sistema
    public EventSystem(String jdbcUrl, String user, String pwd) throws Exception
    
    // Registra un listener para recibir eventos
    public void registerListener(Object listener)
    
    // Publica un evento (serializa, persiste y procesa)
    public void post(Object event) throws Exception
}
```

**Ejemplo completo**:
```java
EventSystem system = new EventSystem(
    "jdbc:postgresql://localhost:5432/eventdb",
    "user",
    "password"
);

// Registrar múltiples listeners
system.registerListener(new EmailNotificationListener());
system.registerListener(new AuditLogger());
system.registerListener(new MetricsCollector());

// Publicar eventos
system.post(new OrderCreatedEvent(orderId, amount));
system.post(new PaymentProcessedEvent(paymentId));
```

### EventWorker

**Descripción**: Worker que hace polling de eventos pendientes y los procesa concurrentemente.

**Características**:
- **Polling cada 1 segundo** de eventos con `status='PENDING'` y `next_attempt_at <= NOW()`
- **Procesamiento en batch** de hasta 20 eventos por iteración
- **Pool de threads configurable** para procesamiento concurrente
- **Deserialización inteligente** usando el campo `event_type` para reconstruir objetos
- **Manejo de fallos** con retry y backoff exponencial
- **Detección de servidor detenido**: Si el servidor se detiene durante la ejecución de un método con `@RetryableSubscribe`, el evento se reintenta automáticamente al reiniciar

**Configuración**:
```java
// Constructor interno (usado por EventSystem)
public EventWorker(
    PostgreSQLEventRepository repo,
    EventBus eventBus,
    int concurrency  // Número de threads para procesar eventos
)
```

**Mecanismo de detección de servidor detenido:**

El sistema usa un `CountDownLatch` para verificar si un método completó su ejecución:

1. **Antes de publicar el evento**: Se crea un latch con valor 1
2. **Cuando el método termina**: El latch baja a 0 (éxito o fallo)
3. **Verificación**: Se espera hasta el `timeoutSeconds` configurado
4. **Si timeout sin completar**: Se verifica si el latch está en 0
   - **Latch = 0**: El método completó (aunque tardó más) → procesa resultado
   - **Latch > 0**: El método NO completó (servidor detenido) → **reintenta evento**

**Backoff exponencial**:
- Intento 1: `2^1 * 1000ms = 2 segundos`
- Intento 2: `2^2 * 1000ms = 4 segundos`
- Intento 3: `2^3 * 1000ms = 8 segundos`
- Intento 4: `2^4 * 1000ms = 16 segundos`
- Intento 5: `2^5 * 1000ms = 32 segundos`

### PostgreSQLEventRepository

**Descripción**: Capa de persistencia que maneja todas las operaciones de base de datos.

**API**:
```java
public class PostgreSQLEventRepository {
    // Constructor: crea la conexión e inicializa el schema
    public PostgreSQLEventRepository(String jdbcUrl, String user, String password)
    
    // Guarda un nuevo evento
    public void saveEvent(String eventType, String payload, int maxAttempts)
    
    // Obtiene eventos pendientes para procesar
    public List<StoredEvent> fetchPendingEvents(int limit)
    
    // Marca un evento como procesado exitosamente
    public void markAsSuccess(long id)
    
    // Incrementa intentos y programa próximo retry
    public void markAsFailed(long id, int attempts, long backoffMs)
}
```

**Schema auto-creado**:
```sql
CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### StoredEvent

**Descripción**: Modelo de datos que representa un evento persistido.

**Estructura**:
```java
public class StoredEvent {
    private Long id;              // ID único del evento
    private String type;          // Nombre completo de la clase (ej: "com.example.UserCreatedEvent")
    private String payload;       // JSON serializado del evento
    private String status;        // PENDING | SUCCESS
    private int attempts;         // Número de intentos realizados
    private int maxAttempts;      // Máximo de intentos permitidos
}
```

## 💡 Uso Detallado

### 🎯 Control de Reintentos con @RetryableSubscribe (NUEVO)

La anotación `@RetryableSubscribe` permite controlar cuándo un evento debe ser marcado como SUCCESS o reintentado:

**Comportamiento**:
- ✅ Si el método se ejecuta sin excepciones → evento marcado como **SUCCESS**
- ❌ Si el método lanza una excepción → evento **reintentado** automáticamente

**Ejemplo básico**:
```java
import com.google.common.eventbus.Subscribe;
import com.rigoberto.pr.Annotations.RetryableSubscribe;

public class PaymentListener {
    
    @Subscribe
    @RetryableSubscribe  // ← El evento solo se marca como SUCCESS si no hay excepciones
    public void processPayment(PaymentEvent event) {
        // Si este método falla, el evento será reintentado
        paymentGateway.charge(event.getAmount());
    }
}
```

**Sin @RetryableSubscribe (comportamiento tradicional)**:
```java
public class LogListener {
    
    @Subscribe  // Sin @RetryableSubscribe
    public void logEvent(AnyEvent event) {
        // Este listener NO reintenta en caso de error
        // El evento se marca como SUCCESS automáticamente
        logger.info("Event: {}", event);
    }
}
```

**Ventajas**:
- ✅ Reintentos automáticos solo cuando el listener falla
- ✅ Compatible con listeners existentes (opcional)
- ✅ Control granular por método
- ✅ Idempotencia requerida para métodos retryables

**📖 Ver guía completa**: [`RETRYABLE_SUBSCRIBE_GUIDE.md`](RETRYABLE_SUBSCRIBE_GUIDE.md)  
**🧪 Ver ejemplo**: [`RetryableSubscribeExample.java`](src/main/java/com/rigoberto/pr/Examples/RetryableSubscribeExample.java)

### Creando Eventos

Los eventos pueden ser cualquier POJO serializable:

```java
public class UserCreatedEvent {
    private String userId;
    private String email;
    private String name;
    private long timestamp;
    
    // Constructor, getters, setters
    public UserCreatedEvent(String userId, String email, String name) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.timestamp = System.currentTimeMillis();
    }
}
```

### Creando Listeners

Los listeners usan la anotación `@Subscribe` de Guava:

```java
import com.google.common.eventbus.Subscribe;

public class UserNotificationListener {
    
    @Subscribe
    public void onUserCreated(UserCreatedEvent event) {
        // Enviar email de bienvenida
        sendWelcomeEmail(event.getEmail(), event.getName());
    }
    
    @Subscribe
    public void onPasswordChanged(PasswordChangedEvent event) {
        // Enviar notificación de seguridad
        sendSecurityAlert(event.getUserId());
    }
}
```

**Características de los listeners**:
- Pueden tener múltiples métodos `@Subscribe`
- Cada método puede escuchar un tipo de evento diferente
- El procesamiento es asíncrono
- Los listeners se ejecutan en threads separados

### Manejo de Errores en Listeners

El sistema ofrece dos estrategias de manejo de errores según tus necesidades:

#### Con @RetryableSubscribe (para operaciones críticas)

```java
@Subscribe
@RetryableSubscribe(timeoutSeconds = 10)
public void onOrderCreated(OrderCreatedEvent event) {
    // Si esto falla o no completa en 10 segundos, se reintenta
    PaymentResult result = paymentService.charge(event.getAmount());
    
    if (!result.isSuccess()) {
        throw new PaymentFailedException("Payment failed: " + result.getError());
    }
}
```

**Comportamiento:**
1. Si el método lanza excepción → el evento se **reintenta** con backoff exponencial
2. Si el método no completa en el `timeoutSeconds` → el evento se **reintenta**
3. Si el servidor se detiene durante la ejecución → el evento se **reintenta** al reiniciar
4. Si el método completa sin excepciones → el evento se marca como **SUCCESS**

#### Sin @RetryableSubscribe (para operaciones no críticas)

```java
@Subscribe
public void onOrderCreated(OrderCreatedEvent event) {
    // Incluso si falla, el evento se marca como SUCCESS
    logger.info("Order created: {}", event.getOrderId());
}
```

**Comportamiento:**
- El evento **SIEMPRE** se marca como SUCCESS, incluso si hay excepciones
- Útil para: logging, métricas, notificaciones no críticas

#### Parámetros de @RetryableSubscribe

```java
@RetryableSubscribe(
    timeoutSeconds = 30,        // Timeout personalizado (default: 5)
    propagateException = true   // Propagar excepción (default: false)
)
```

**Límite de reintentos:**
- Después de `max_attempts` (default: 5), el evento se marca como `FAILED`
- Los reintentos usan backoff exponencial: 2s, 4s, 8s, 16s, 32s...

### Ejemplo Completo: Sistema de Órdenes

```java
// 1. Definir eventos
public class OrderCreatedEvent {
    private String orderId;
    private BigDecimal amount;
    private String customerId;
    // constructor, getters
}

public class PaymentProcessedEvent {
    private String orderId;
    private String paymentId;
    private boolean success;
    // constructor, getters
}

// 2. Crear listeners
public class OrderEventListeners {
    
    @Subscribe
    public void onOrderCreated(OrderCreatedEvent event) {
        // Procesar pago
        log.info("Processing payment for order: {}", event.getOrderId());
        paymentService.processPayment(event.getOrderId(), event.getAmount());
    }
    
    @Subscribe
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        if (event.isSuccess()) {
            // Enviar confirmación
            emailService.sendOrderConfirmation(event.getOrderId());
            // Actualizar inventario
            inventoryService.reserveItems(event.getOrderId());
        } else {
            // Manejar pago fallido
            notificationService.notifyPaymentFailure(event.getOrderId());
        }
    }
}

// 3. Inicializar y usar
public class OrderService {
    private final EventSystem eventSystem;
    
    public OrderService() throws Exception {
        this.eventSystem = new EventSystem(
            System.getenv("DB_URL"),
            System.getenv("DB_USER"),
            System.getenv("DB_PASSWORD")
        );
        
        // Registrar listeners
        eventSystem.registerListener(new OrderEventListeners());
    }
    
    public void createOrder(Order order) throws Exception {
        // Guardar orden en DB
        orderRepository.save(order);
        
        // Publicar evento (persistido y procesado asíncronamente)
        eventSystem.post(new OrderCreatedEvent(
            order.getId(),
            order.getAmount(),
            order.getCustomerId()
        ));
    }
}
```

## 🗄️ Base de Datos

### Schema

La tabla `events` se crea automáticamente al inicializar el sistema:

```sql
CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Campos

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | BIGSERIAL | ID único auto-incremental |
| `event_type` | VARCHAR(255) | Nombre completo de la clase del evento |
| `payload` | TEXT | JSON serializado del evento |
| `status` | VARCHAR(20) | Estado: `PENDING` o `SUCCESS` |
| `attempts` | INT | Número de intentos de procesamiento |
| `max_attempts` | INT | Máximo de intentos permitidos (default: 5) |
| `next_attempt_at` | TIMESTAMP | Momento del próximo intento |
| `created_at` | TIMESTAMP | Momento de creación del evento |

### Estados de Eventos

```
┌─────────┐
│ PENDING │ ← Estado inicial al publicar un evento
└────┬────┘
     │
     │  EventWorker polling
     ▼
┌─────────────┐
│ Processing  │ (no persiste, solo en memoria)
└──────┬──────┘
       │
       ├── ✅ Success ──────► ┌─────────┐
       │                      │ SUCCESS │ (permanente)
       │                      └─────────┘
       │
       └── ❌ Failure ──────► ┌─────────┐
                              │ PENDING │ (retry con backoff)
                              └─────────┘
```

### Consultas Útiles

**Ver eventos pendientes**:
```sql
SELECT id, event_type, attempts, max_attempts, next_attempt_at 
FROM events 
WHERE status = 'PENDING'
ORDER BY next_attempt_at;
```

**Ver eventos que fallaron múltiples veces**:
```sql
SELECT id, event_type, attempts, max_attempts, created_at
FROM events 
WHERE attempts >= 3 AND status = 'PENDING'
ORDER BY attempts DESC;
```

**Ver eventos que excedieron máximo de intentos**:
```sql
SELECT id, event_type, attempts, max_attempts, created_at, payload
FROM events 
WHERE attempts >= max_attempts
ORDER BY created_at DESC;
```

**Estadísticas de eventos**:
```sql
SELECT 
    status,
    COUNT(*) as count,
    AVG(attempts) as avg_attempts
FROM events
GROUP BY status;
```

### Índices Recomendados (Producción)

```sql
-- Para mejorar el polling de eventos pendientes
CREATE INDEX idx_events_pending 
ON events(status, next_attempt_at) 
WHERE status = 'PENDING';

-- Para consultas por tipo de evento
CREATE INDEX idx_events_type 
ON events(event_type);

-- Para consultas temporales
CREATE INDEX idx_events_created 
ON events(created_at DESC);
```

## ⚙️ Configuración

### Variables de Entorno

```bash
# Database
export DB_URL="jdbc:postgresql://localhost:5432/eventdb"
export DB_USER="eventuser"
export DB_PASSWORD="eventpass"

# Worker Configuration
export WORKER_CONCURRENCY=5        # Threads para procesamiento
export EVENT_BATCH_SIZE=20         # Eventos por iteración
export POLLING_INTERVAL_MS=1000    # Intervalo de polling
export MAX_RETRY_ATTEMPTS=5        # Máximo de reintentos
```

### Personalización

```java
// Ajustar concurrencia según carga
int concurrency = Integer.parseInt(
    System.getenv().getOrDefault("WORKER_CONCURRENCY", "5")
);

// Ajustar max_attempts por evento
eventSystem.post(event, 10); // 10 intentos máximo
```

### Configuración de Timeout por Método

Cada listener puede tener su propio timeout configurado:

```java
@Subscribe
@RetryableSubscribe(timeoutSeconds = 3)   // Operación rápida
public void handleQuickTask(QuickEvent event) {
    quickService.process(event);
}

@Subscribe
@RetryableSubscribe(timeoutSeconds = 30)  // Operación lenta
public void handleLongTask(LongTaskEvent event) {
    heavyProcessingService.process(event);
}
```

**Recomendaciones de timeout:**
- Operaciones rápidas (< 1s): `timeoutSeconds = 3`
- Operaciones normales (1-5s): `timeoutSeconds = 5` (default)
- Operaciones lentas (> 5s): `timeoutSeconds = 10` o más
- Operaciones muy lentas: `timeoutSeconds = 30` o más

**¿Qué pasa si se excede el timeout?**
- Si el método **completa después del timeout** pero **antes de la verificación**: se procesa normalmente
- Si el método **no completa** (ej: servidor detenido): el evento se **reintenta**

## 🧪 Tests

### Tests Unitarios

```bash
mvn test
```

### Tests de Integración

El proyecto incluye tests de integración completos usando Testcontainers.

**Opción 1: Con Testcontainers (Recomendado)**
```bash
mvn test -Pintegration-tests
```

**Opción 2: Con script automático**
```bash
./run-integration-tests.sh
```

**Opción 3: Manual con PostgreSQL externo**
```bash
# 1. Iniciar PostgreSQL
docker run -d --name test-postgres \
  -e POSTGRES_DB=testdb \
  -e POSTGRES_USER=testuser \
  -e POSTGRES_PASSWORD=testpass \
  -p 5432:5432 \
  postgres:15-alpine

# 2. Habilitar el test manual
# Remover @Disabled en EventSystemManualTest.java

# 3. Ejecutar tests
mvn test -Dtest=EventSystemManualTest

# 4. Limpiar
docker stop test-postgres && docker rm test-postgres
```

Ver [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md) para más detalles.

### Estructura de Tests

```
src/test/java/com/rigoberto/pr/Workers/
├── EventSystemIntegrationTest.java  # Tests con Testcontainers
└── EventSystemManualTest.java       # Tests manuales con PostgreSQL externo
```

## 🔨 Build y Deployment

### Build Local

```bash
# Compilar sin firmar
mvn clean package

# Verificar con tests
mvn clean verify

# Tests de integración
./run-integration-tests.sh
```

### Publicación en Maven Central

Ver documentación completa:
- [MAVEN_CENTRAL_DEPLOYMENT.md](MAVEN_CENTRAL_DEPLOYMENT.md) - Guía paso a paso
- [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md) - Checklist detallado
- [QUICK_START.md](QUICK_START.md) - Comandos rápidos

**Resumen rápido**:

```bash
# 1. Preparación (una sola vez)
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Configurar ~/.m2/settings.xml con credenciales de Sonatype

# 2. Actualizar versión en pom.xml (sin -SNAPSHOT)

# 3. Desplegar con script interactivo
./deploy.sh
# Selecciona opción 2 (Release)

# O comando directo
mvn clean deploy -P release

# 4. Validar y liberar en https://s01.oss.sonatype.org/
# Staging Repositories → Close → Release
```

### Perfiles Maven

- **`default`**: Build local sin firmar
- **`release`**: Build con firma GPG y deployment a Maven Central
- **`integration-tests`**: Ejecuta tests de integración con Testcontainers

## 📁 Estructura del Proyecto

```
eventbus/
├── src/
│   ├── main/
│   │   └── java/com/rigoberto/pr/
│   │       ├── Models/
│   │       │   └── StoredEvent.java           # Modelo de evento persistido
│   │       ├── Repositories/
│   │       │   └── PostgreSQLEventRepository.java  # Capa de persistencia
│   │       └── Workers/
│   │           ├── EventSystem.java           # API principal
│   │           └── EventWorker.java           # Worker de procesamiento
│   └── test/
│       └── java/com/rigoberto/pr/Workers/
│           ├── EventSystemIntegrationTest.java    # Tests con Testcontainers
│           └── EventSystemManualTest.java         # Tests manuales
├── pom.xml                                    # Configuración Maven
├── README.md                                  # Esta documentación
├── QUICK_START.md                             # Guía rápida
├── INTEGRATION_TESTS.md                       # Guía de tests
├── MAVEN_CENTRAL_DEPLOYMENT.md                # Guía de deployment
├── DEPLOYMENT_CHECKLIST.md                    # Checklist de deployment
├── MAVEN_CENTRAL_READY.md                     # Estado de deployment
├── CONFIGURATION_EXAMPLES.md                  # Ejemplos de configuración
├── LICENSE                                    # Licencia MIT
├── deploy.sh                                  # Script de deployment
└── run-integration-tests.sh                   # Script de tests
```

### Descripción de Componentes

**Models**:
- `StoredEvent.java`: POJO que representa un evento en la base de datos

**Repositories**:
- `PostgreSQLEventRepository.java`: Maneja todas las operaciones CRUD de eventos

**Workers**:
- `EventSystem.java`: Fachada principal, inicializa y coordina componentes
- `EventWorker.java`: Polling loop que procesa eventos asíncronamente

## 📚 Dependencias

| Dependencia | Versión | Propósito |
|-------------|---------|-----------|
| [Google Guava](https://github.com/google/guava) | 23.0 | EventBus asíncrono |
| [PostgreSQL JDBC](https://jdbc.postgresql.org/) | 42.2.8 | Driver de base de datos |
| [Jackson Databind](https://github.com/FasterXML/jackson-databind) | 2.15.2 | Serialización JSON |
| [JUnit Jupiter](https://junit.org/junit5/) | 5.10.0 | Framework de tests |
| [Testcontainers](https://www.testcontainers.org/) | 1.19.1 | Tests de integración |

### Actualizar Dependencias

```bash
# Ver dependencias desactualizadas
mvn versions:display-dependency-updates

# Actualizar a versiones específicas
mvn versions:use-latest-versions
```

## 🗺️ Roadmap

### v1.1.0 (Próximo)
- [ ] Dead Letter Queue (DLQ) para eventos que exceden max_attempts
- [ ] Métricas con Micrometer (eventos procesados, tasa de error, latencia)
- [ ] Health checks y endpoints de monitoreo

### v1.2.0
- [ ] Soporte para prioridades de eventos (HIGH, NORMAL, LOW)
- [ ] Filtros y interceptores de eventos
- [ ] Soporte para transacciones distribuidas (saga pattern)

### v2.0.0
- [ ] Soporte para múltiples storage backends (MongoDB, Redis, etc.)
- [ ] API REST para gestión de eventos
- [ ] Dashboard web de administración
- [ ] Particionamiento de tabla events por fecha
- [ ] Soporte para event sourcing completo

### Ideas Futuras
- [ ] Integración con Kafka para eventos de alto volumen
- [ ] Soporte para eventos scheduled (cron)
- [ ] Webhook support para eventos externos
- [ ] GraphQL API para consultas de eventos

## 🤝 Contribuir

¡Las contribuciones son bienvenidas! Por favor sigue estos pasos:

1. **Fork el proyecto**
   ```bash
   git clone https://github.com/rigotra1984/guava-eventbus.git
   cd guava-eventbus/eventbus
   ```

2. **Crea una rama para tu feature**
   ```bash
   git checkout -b feature/amazing-feature
   ```

3. **Realiza tus cambios y tests**
   ```bash
   # Asegúrate de que pasan todos los tests
   mvn clean verify
   ./run-integration-tests.sh
   ```

4. **Commit con mensajes descriptivos**
   ```bash
   git commit -m "feat: Add amazing feature"
   ```

5. **Push a tu fork**
   ```bash
   git push origin feature/amazing-feature
   ```

6. **Abre un Pull Request**

### Guías de Contribución

- Sigue las convenciones de código Java
- Incluye tests para nuevas funcionalidades
- Actualiza la documentación según sea necesario
- Usa [Conventional Commits](https://www.conventionalcommits.org/)

### Reportar Bugs

Abre un issue con:
- Descripción clara del problema
- Pasos para reproducirlo
- Comportamiento esperado vs actual
- Versión de Java, PostgreSQL y librería
- Stack trace si aplica

## 📄 Licencia

Este proyecto está licenciado bajo la **MIT License** - ver el archivo [LICENSE](LICENSE) para más detalles.

```
MIT License

Copyright (c) 2025 Rigoberto

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

## 👨‍💻 Autor

**Rigoberto**
- GitHub: [@rigotra1984](https://github.com/rigotra1984)
- Repository: [guava-eventbus](https://github.com/rigotra1984/guava-eventbus)

## 🙏 Agradecimientos

- **Google Guava** - Por proporcionar un excelente EventBus asíncrono
- **PostgreSQL** - Por la robusta base de datos
- **Testcontainers** - Por facilitar los tests de integración
- **Jackson** - Por la serialización JSON eficiente
- **Maven Community** - Por las herramientas de build y deployment

---

<div align="center">

**⭐ Si este proyecto te resulta útil, considera darle una estrella en GitHub ⭐**

[Reportar Bug](https://github.com/rigotra1984/guava-eventbus/issues) • 
[Solicitar Feature](https://github.com/rigotra1984/guava-eventbus/issues) • 
[Contribuir](https://github.com/rigotra1984/guava-eventbus/pulls)

</div>

