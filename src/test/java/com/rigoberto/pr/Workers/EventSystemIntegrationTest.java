package com.rigoberto.pr.Workers;

import com.google.common.eventbus.Subscribe;
import com.rigoberto.pr.Models.StoredEvent;
import com.rigoberto.pr.Repositories.PostgreSQLEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * Test de integración para EventSystem usando Testcontainers con PostgreSQL.
 * 
 * Este test requiere Docker disponible para ejecutarse.
 * 
 * Para ejecutarlo con Docker:
 * mvn test -Dtest=EventSystemIntegrationTest
 * 
 * O habilitar el perfil integration-tests:
 * mvn test -Pintegration-tests
 * 
 * Si Docker no está disponible, usa EventSystemManualTest con una base de datos PostgreSQL externa.
 */
@Testcontainers
class EventSystemIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private EventSystem eventSystem;
    private String jdbcUrl;
    private String user;
    private String password;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = postgres.getJdbcUrl();
        user = postgres.getUsername();
        password = postgres.getPassword();

        eventSystem = new EventSystem(jdbcUrl, user, password);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up after tests
        try (Connection con = DriverManager.getConnection(jdbcUrl, user, password);
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
        try (Connection con = DriverManager.getConnection(jdbcUrl, user, password);
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

        try (Connection con = DriverManager.getConnection(jdbcUrl, user, password);
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
        PostgreSQLEventRepository repo = new PostgreSQLEventRepository(jdbcUrl, user, password);
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
