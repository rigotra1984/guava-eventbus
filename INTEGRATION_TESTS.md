# Tests de Integración con Testcontainers

## Resumen

Este proyecto incluye tests de integración completos para `EventSystem` que verifican el funcionamiento end-to-end del sistema de eventos persistentes con PostgreSQL.

## Test de Integración: EventSystemIntegrationTest

Este test de integración verifica el funcionamiento completo de `EventSystem` usando Testcontainers con PostgreSQL.

### Características del Test

El test `EventSystemIntegrationTest` incluye las siguientes pruebas:

1. **testEventSystemCreatesSchemaAndPostsEvent**: Verifica que el EventSystem crea el schema de la base de datos y guarda eventos correctamente.

2. **testEventSystemProcessesEventAndCallsListener**: Verifica que los eventos publicados son procesados y enviados a los listeners registrados.

3. **testEventSystemMarksEventAsSuccessAfterProcessing**: Verifica que los eventos procesados exitosamente se marcan como SUCCESS en la base de datos.

4. **testMultipleEventsAreProcessed**: Verifica que múltiples eventos pueden ser procesados concurrentemente.

5. **testEventPersistenceAndRetrieval**: Verifica la persistencia de eventos y su recuperación desde el repositorio.

### Requisitos para Ejecutar los Tests

#### Configuración Actual

Por defecto, los tests de integración están **excluidos** del build para que no fallen cuando Docker no está disponible. El build normal ejecutará solo los tests unitarios:

```bash
# Ejecutar tests unitarios (sin tests de integración)
mvn clean test
```

#### Opción 1: Ejecutar tests de integración con Docker disponible

Si tienes Docker disponible en tu entorno, activa el perfil `integration-tests`:

```bash
# Ejecutar tests de integración con el perfil
mvn clean test -Pintegration-tests

# O ejecutar solo los tests de integración específicos
mvn test -Dtest=EventSystemIntegrationTest
```

#### Opción 2: Ejecutar en Dev Container con Docker

Si estás en un dev container sin acceso directo a Docker, necesitas:

1. **Montar el socket de Docker del host** en el dev container agregando esto al `devcontainer.json`:
   ```json
   {
     "mounts": [
       "source=/var/run/docker.sock,target=/var/run/docker.sock,type=bind"
     ]
   }
   ```

2. **Instalar Docker CLI en el contenedor**:
   ```bash
   apt-get update && apt-get install -y docker.io
   ```

#### Opción 3: Usar PostgreSQL externo con script

La forma más sencilla si tienes Docker disponible en el host:

```bash
# Ejecutar el script que gestiona todo automáticamente
./run-integration-tests.sh
```

Este script:
1. Inicia un contenedor PostgreSQL temporal
2. Espera a que esté listo
3. Ejecuta los tests manuales (`EventSystemManualTest`)
4. Limpia el contenedor al finalizar

#### Opción 4: PostgreSQL manual

Si prefieres gestionar PostgreSQL manualmente:

1. Iniciar PostgreSQL manualmente:
   ```bash
   docker run -d --name test-postgres \
     -e POSTGRES_DB=testdb \
     -e POSTGRES_USER=testuser \
     -e POSTGRES_PASSWORD=testpass \
     -p 5432:5432 \
     postgres:15-alpine
   ```

2. Modificar el test para usar la instancia manual:
   ```bash
   # Habilitar el test manual removiendo @Disabled de EventSystemManualTest
   # Luego ejecutar:
   mvn test -Dtest=EventSystemManualTest
   ```

3. Detener PostgreSQL cuando termines:
   ```bash
   docker stop test-postgres && docker rm test-postgres
   ```

## Tipos de Tests Disponibles

### 1. EventSystemIntegrationTest (Testcontainers)
- **Ubicación**: `src/test/java/com/rigoberto/pr/Workers/EventSystemIntegrationTest.java`
- **Requiere**: Docker disponible
- **Ventajas**: Completamente automático, no requiere configuración externa
- **Ejecución**: `mvn test -Pintegration-tests`

### 2. EventSystemManualTest (PostgreSQL externo)
- **Ubicación**: `src/test/java/com/rigoberto/pr/Workers/EventSystemManualTest.java`
- **Requiere**: PostgreSQL ejecutándose manualmente
- **Ventajas**: Funciona sin Testcontainers
- **Ejecución**: `./run-integration-tests.sh` o `mvn test -Dtest=EventSystemManualTest`
- **Nota**: Está deshabilitado por defecto con `@Disabled`, remover la anotación para habilitarlo

### Estructura del Test

El test utiliza:
- **@Testcontainers**: Anotación que habilita el soporte de Testcontainers para JUnit 5
- **@Container**: Marca el contenedor PostgreSQL para que sea gestionado por Testcontainers
- **CountDownLatch**: Para esperar de forma asíncrona a que los eventos sean procesados
- **@Subscribe**: Anotación de Guava EventBus para registrar listeners

### Componentes de Prueba

- **TestEvent**: Clase simple para eventos de prueba con id y mensaje
- **TestEventListener**: Listener que captura un único evento
- **MultiEventListener**: Listener que puede capturar múltiples eventos

### Notas Importantes

1. Testcontainers descarga automáticamente la imagen de PostgreSQL si no existe
2. Cada test limpia la base de datos después de ejecutarse
3. Los tests esperan hasta 5-10 segundos para que los eventos sean procesados
4. El contenedor PostgreSQL se destruye automáticamente después de los tests

### Solución de Problemas

**Error: "Could not find a valid Docker environment"**
- Verifica que Docker esté instalado y en ejecución
- Verifica que tu usuario tenga permisos para acceder al socket de Docker
- En dev containers, asegúrate de montar el socket de Docker del host

**Tests timeout**
- Los tests usan CountDownLatch con timeouts de 5-10 segundos
- Si los tests fallan por timeout, puede ser que el EventWorker no esté iniciando correctamente
- Verifica los logs para ver errores en el procesamiento de eventos

**Eventos no se procesan**
- El EventWorker ejecuta cada segundo para buscar eventos pendientes
- Asegúrate de que next_attempt_at sea <= NOW() en la base de datos
- Verifica que no haya excepciones en el procesamiento
