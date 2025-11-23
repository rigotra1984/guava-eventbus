# EventBus - Sistema de Eventos Persistentes

[![Maven Central](https://img.shields.io/maven-central/v/com.rigoberto.pr/eventbus.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.rigoberto.pr%22%20AND%20a:%22eventbus%22)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

Sistema de eventos asíncronos con persistencia en PostgreSQL, retry automático con backoff exponencial y procesamiento concurrente.

## Instalación

Agrega la dependencia a tu proyecto Maven:

```xml
<dependency>
    <groupId>com.rigoberto.pr</groupId>
    <artifactId>eventbus</artifactId>
    <version>1.0.0</version>
</dependency>
```

O si usas Gradle:

```gradle
implementation 'com.rigoberto.pr:eventbus:1.0.0'
```

## Características

- **EventBus asíncrono**: Basado en Guava EventBus para publicación y suscripción de eventos
- **Persistencia**: Almacenamiento de eventos en PostgreSQL para garantizar no perder eventos
- **Retry automático**: Reintentos con backoff exponencial en caso de fallos
- **Procesamiento concurrente**: Pool de workers para procesar múltiples eventos simultáneamente
- **Tests de integración**: Suite completa de tests con Testcontainers

## Arquitectura

```
┌─────────────┐
│  EventSystem │
└──────┬──────┘
       │
       ├──────────────┐
       ▼              ▼
┌─────────────┐  ┌────────────────┐
│  EventBus   │  │ PostgreSQL Repo │
│  (Guava)    │  │                │
└──────┬──────┘  └────────┬───────┘
       │                  │
       │         ┌────────▼────────┐
       │         │   PostgreSQL    │
       │         │   (events table)│
       │         └────────▲────────┘
       │                  │
       │         ┌────────┴────────┐
       └─────────►  EventWorker    │
                 │  (polling +     │
                 │   processing)   │
                 └─────────────────┘
```

## Componentes

### EventSystem
Punto de entrada principal que coordina el EventBus y el EventWorker.

```java
EventSystem eventSystem = new EventSystem(jdbcUrl, user, password);
eventSystem.registerListener(myListener);
eventSystem.post(myEvent);
```

### EventWorker
Worker que:
1. Hace polling cada segundo de eventos pendientes
2. Procesa eventos de forma concurrente
3. Maneja reintentos con backoff exponencial
4. Marca eventos como SUCCESS o actualiza el contador de intentos

### PostgreSQLEventRepository
Maneja la persistencia de eventos:
- `saveEvent()`: Guarda nuevos eventos
- `fetchPendingEvents()`: Obtiene eventos pendientes para procesar
- `markAsSuccess()`: Marca eventos procesados exitosamente
- `markAsFailed()`: Actualiza intentos y programa próximo reintento

## Uso

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
EventSystem eventSystem = new EventSystem(
    "jdbc:postgresql://localhost:5432/eventdb",
    "eventuser",
    "eventpass"
);
```

### 3. Crear y registrar listeners

```java
public class MyEventListener {
    @Subscribe
    public void handleMyEvent(MyEvent event) {
        System.out.println("Evento recibido: " + event);
    }
}

eventSystem.registerListener(new MyEventListener());
```

### 4. Publicar eventos

```java
MyEvent event = new MyEvent("data");
eventSystem.post(event);
```

## Schema de Base de Datos

```sql
CREATE TABLE events (
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

### Estados de eventos

- **PENDING**: Evento esperando ser procesado
- **SUCCESS**: Evento procesado exitosamente

### Reintentos

- Backoff exponencial: `2^attempt * 1000ms`
- Máximo de intentos configurable (default: 5)
- Después del máximo de intentos, el evento permanece marcado con `attempts >= max_attempts`

## Build y Tests

### Build del proyecto

```bash
mvn clean package
```

### Tests unitarios

```bash
mvn test
```

### Tests de integración

Ver [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md) para instrucciones detalladas.

**Opción 1: Con Testcontainers (requiere Docker)**
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

# 2. Habilitar el test (remover @Disabled en EventSystemManualTest)

# 3. Ejecutar tests
mvn test -Dtest=EventSystemManualTest

# 4. Limpiar
docker stop test-postgres && docker rm test-postgres
```

## Dependencias

- **Guava 23.0**: Para el EventBus asíncrono
- **PostgreSQL JDBC 42.2.8**: Driver de base de datos
- **org.json 20190722**: Serialización de eventos
- **JUnit Jupiter 5.10.0**: Tests (scope: test)
- **Testcontainers 1.19.1**: Tests de integración (scope: test)

## Estructura del Proyecto

```
eventbus/
├── src/
│   ├── main/java/com/rigoberto/pr/
│   │   ├── App.java
│   │   ├── Models/
│   │   │   └── StoredEvent.java
│   │   ├── Repositories/
│   │   │   └── PostgreSQLEventRepository.java
│   │   └── Workers/
│   │       ├── EventSystem.java
│   │       └── EventWorker.java
│   └── test/java/com/rigoberto/pr/
│       ├── AppTest.java
│       └── Workers/
│           ├── EventSystemIntegrationTest.java
│           └── EventSystemManualTest.java
├── pom.xml
├── INTEGRATION_TESTS.md
├── README.md
└── run-integration-tests.sh
```

## Mejoras Futuras

- [ ] Dead Letter Queue para eventos que exceden max_attempts
- [ ] Métricas y monitoreo (eventos procesados, tasa de error, latencia)
- [ ] Soporte para prioridades de eventos
- [ ] Particionamiento de tabla events por fecha
- [ ] API REST para consultar estado de eventos
- [ ] Dashboard de administración
- [ ] Soporte para múltiples tipos de storage backends

## Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## Publicación en Maven Central

Para publicar este proyecto en Maven Central, consulta:

- **[MAVEN_CENTRAL_DEPLOYMENT.md](MAVEN_CENTRAL_DEPLOYMENT.md)** - Guía completa paso a paso
- **[DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md)** - Checklist detallado
- **[deploy.sh](deploy.sh)** - Script de ayuda para deployment

### Resumen rápido

```bash
# 1. Configurar GPG y credenciales de Sonatype (solo una vez)
# Ver MAVEN_CENTRAL_DEPLOYMENT.md para detalles

# 2. Actualizar versión en pom.xml (sin -SNAPSHOT)

# 3. Desplegar
./deploy.sh

# 4. Validar y liberar en https://s01.oss.sonatype.org/
```

## Licencia

Este proyecto está licenciado bajo la Licencia MIT - ver el archivo [LICENSE](LICENSE) para más detalles.

## Autor

**Rigoberto** - [rigotra1984](https://github.com/rigotra1984)

## Agradecimientos

- Google Guava por el excelente EventBus
- PostgreSQL por la robusta base de datos
- Testcontainers por facilitar los tests de integración

