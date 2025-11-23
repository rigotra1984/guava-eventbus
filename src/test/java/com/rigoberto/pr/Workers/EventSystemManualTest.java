package com.rigoberto.pr.Workers;

import com.google.common.eventbus.Subscribe;
import com.rigoberto.pr.Models.StoredEvent;
import com.rigoberto.pr.Repositories.PostgreSQLEventRepository;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración manual para EventSystem.
 * 
 * Este test requiere una instancia de PostgreSQL ejecutándose.
 * Para ejecutarlo, primero inicia PostgreSQL:
 * 
 * docker run -d --name test-postgres \
 *   -e POSTGRES_DB=testdb \
 *   -e POSTGRES_USER=testuser \
 *   -e POSTGRES_PASSWORD=testpass \
 *   -p 5432:5432 \
 *   postgres:15-alpine
 * 
 * Luego ejecuta el test:
 * mvn test -Dtest=EventSystemManualTest
 * 
 * Para detener y eliminar el contenedor:
 * docker stop test-postgres && docker rm test-postgres
 */
@Disabled("Requiere PostgreSQL ejecutándose manualmente - habilitar solo para pruebas manuales")
class EventSystemManualTest {

    // Configuración de la base de datos - ajustar según tu entorno
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/testdb";
    private static final String USER = "testuser";
    private static final String PASSWORD = "testpass";

    private EventSystem eventSystem;

    @BeforeEach
    void setUp() throws Exception {
        eventSystem = new EventSystem(JDBC_URL, USER, PASSWORD);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Limpiar después de cada test
        try (Connection con = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM events");
        }
    }

    @Test
    void testEventSystemCreatesSchemaAndPostsEvent() throws Exception {
        // Given: un evento de prueba
        TestEvent event = new TestEvent("test-id", "test-message");

        // When: publicamos el evento
        eventSystem.post(event);

        // Then: el evento debe estar guardado en la base de datos
        try (Connection con = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement st = con.createStatement()) {
            
            ResultSet rs = st.executeQuery("SELECT COUNT(*) as count FROM events WHERE status='PENDING'");
            rs.next();
            assertEquals(1, rs.getInt("count"), "Debe haber 1 evento pendiente");

            ResultSet rs2 = st.executeQuery("SELECT event_type, payload FROM events");
            rs2.next();
            assertEquals(TestEvent.class.getName(), rs2.getString("event_type"));
            assertTrue(rs2.getString("payload").contains("test-id"));
            assertTrue(rs2.getString("payload").contains("test-message"));
        }
    }

    @Test
    void testEventSystemProcessesEventAndCallsListener() throws Exception {
        // Given: un listener que captura eventos
        TestEventListener listener = new TestEventListener();
        eventSystem.registerListener(listener);

        // Given: un evento de prueba
        TestEvent event = new TestEvent("event-123", "Hello World");

        // When: publicamos el evento
        eventSystem.post(event);

        // Then: el listener debe recibir el evento procesado
        boolean received = listener.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "El listener debe recibir el evento en menos de 5 segundos");
        assertFalse(listener.receivedEvents.isEmpty(), "Debe haber al menos un evento recibido");
    }

    @Test
    void testEventSystemMarksEventAsSuccessAfterProcessing() throws Exception {
        // Given: un listener registrado
        TestEventListener listener = new TestEventListener();
        eventSystem.registerListener(listener);

        // Given: un evento de prueba
        TestEvent event = new TestEvent("event-456", "Success Test");
        eventSystem.post(event);

        // When: esperamos a que el evento sea procesado
        boolean received = listener.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "El evento debe ser procesado");

        // Then: el evento debe estar marcado como SUCCESS en la base de datos
        Thread.sleep(500); // pequeña espera para asegurar que se marque como success

        try (Connection con = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             Statement st = con.createStatement()) {
            
            ResultSet rs = st.executeQuery("SELECT status FROM events WHERE event_type='" + TestEvent.class.getName() + "'");
            rs.next();
            assertEquals("SUCCESS", rs.getString("status"), "El evento debe estar marcado como SUCCESS");
        }
    }

    @Test
    void testMultipleEventsAreProcessed() throws Exception {
        // Given: un listener que puede recibir múltiples eventos
        int eventCount = 5;
        MultiEventListener listener = new MultiEventListener(eventCount);
        eventSystem.registerListener(listener);

        // When: publicamos múltiples eventos
        for (int i = 0; i < eventCount; i++) {
            TestEvent event = new TestEvent("event-" + i, "Message " + i);
            eventSystem.post(event);
        }

        // Then: todos los eventos deben ser procesados
        boolean allReceived = listener.latch.await(10, TimeUnit.SECONDS);
        assertTrue(allReceived, "Todos los eventos deben ser procesados en menos de 10 segundos");
        assertEquals(eventCount, listener.receivedEvents.size(), "Deben haberse recibido todos los eventos");
    }

    @Test
    void testEventPersistenceAndRetrieval() throws Exception {
        // Given: varios eventos guardados
        eventSystem.post(new TestEvent("event-1", "Message 1"));
        eventSystem.post(new TestEvent("event-2", "Message 2"));
        eventSystem.post(new TestEvent("event-3", "Message 3"));

        // When: consultamos los eventos pendientes directamente del repositorio
        PostgreSQLEventRepository repo = new PostgreSQLEventRepository(JDBC_URL, USER, PASSWORD);
        List<StoredEvent> pendingEvents = repo.fetchPendingEvents(10);

        // Then: deben existir los eventos pendientes
        assertTrue(pendingEvents.size() >= 3, "Debe haber al menos 3 eventos pendientes");
    }

    // Event de prueba
    public static class TestEvent {
        private final String id;
        private final String message;

        public TestEvent(String id, String message) {
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

    // Listener simple para un evento
    public static class TestEventListener {
        public final CountDownLatch latch = new CountDownLatch(1);
        public final List<String> receivedEvents = new ArrayList<>();

        @Subscribe
        public void handleEvent(String event) {
            receivedEvents.add(event);
            latch.countDown();
        }
    }

    // Listener para múltiples eventos
    public static class MultiEventListener {
        public final CountDownLatch latch;
        public final List<String> receivedEvents = new ArrayList<>();

        public MultiEventListener(int expectedCount) {
            this.latch = new CountDownLatch(expectedCount);
        }

        @Subscribe
        public void handleEvent(String event) {
            receivedEvents.add(event);
            latch.countDown();
        }
    }
}
