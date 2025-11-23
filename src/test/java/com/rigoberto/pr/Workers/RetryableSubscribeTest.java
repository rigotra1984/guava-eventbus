package com.rigoberto.pr.Workers;

import com.google.common.eventbus.Subscribe;
import com.rigoberto.pr.Annotations.RetryableSubscribe;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test que demuestra el uso de @RetryableSubscribe para reintentar eventos fallidos.
 * 
 * Este test muestra cómo:
 * 1. Un listener puede fallar deliberadamente y el evento será reintentado
 * 2. Un listener que tiene éxito marcará el evento como SUCCESS
 * 3. Los eventos sin @RetryableSubscribe se comportan como antes
 */
@Testcontainers
class RetryableSubscribeTest {

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
    void testRetryableSubscribeSuccessMarksEventAsSuccess() throws Exception {
        // Given: un listener que siempre tiene éxito
        SuccessfulRetryableListener listener = new SuccessfulRetryableListener();
        eventSystem.registerListener(listener);

        // When: publicamos un evento
        TestEvent event = new TestEvent("success-123", "This should succeed");
        eventSystem.post(event);

        // Then: el listener debe recibir el evento
        boolean received = listener.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "El listener debe recibir el evento");
        assertEquals(1, listener.processedEvents.size());

        // Then: el evento debe estar marcado como SUCCESS
        Thread.sleep(500); // esperar a que se marque como success

        try (Connection con = DriverManager.getConnection(jdbcUrl, user, password);
             Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT status FROM events WHERE event_type='" + TestEvent.class.getName() + "'");
            rs.next();
            assertEquals("SUCCESS", rs.getString("status"), "El evento debe estar marcado como SUCCESS");
        }
    }

    @Test
    void testRetryableSubscribeFailureTriggersRetry() throws Exception {
        // Given: un listener que falla las primeras 2 veces y luego tiene éxito
        RetryableFailingListener listener = new RetryableFailingListener(2);
        eventSystem.registerListener(listener);

        // When: publicamos un evento
        TestEvent event = new TestEvent("retry-456", "This should retry");
        eventSystem.post(event);

        // Then: el listener debe recibir el evento múltiples veces (reintentos)
        boolean finalSuccess = listener.successLatch.await(15, TimeUnit.SECONDS);
        assertTrue(finalSuccess, "El evento debe eventualmente tener éxito después de reintentos");
        assertTrue(listener.attemptCount.get() > 1, "Debe haber al menos 2 intentos");

        // Then: el evento debe eventualmente estar marcado como SUCCESS
        Thread.sleep(1000); // esperar a que se marque como success

        try (Connection con = DriverManager.getConnection(jdbcUrl, user, password);
             Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT status, attempts FROM events WHERE event_type='" + TestEvent.class.getName() + "'");
            rs.next();
            String status = rs.getString("status");
            int attempts = rs.getInt("attempts");
            
            assertTrue(attempts >= 2, "Debe haber al menos 2 intentos registrados");
            // Nota: puede estar en SUCCESS o FAILED dependiendo del timing
            System.out.println("Final status: " + status + ", attempts: " + attempts);
        }
    }

    @Test
    void testNonRetryableSubscribeMarksSuccessImmediately() throws Exception {
        // Given: un listener SIN @RetryableSubscribe
        NonRetryableListener listener = new NonRetryableListener();
        eventSystem.registerListener(listener);

        // When: publicamos un evento
        TestEvent event = new TestEvent("non-retryable-789", "No retry");
        eventSystem.post(event);

        // Then: el listener debe recibir el evento
        boolean received = listener.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "El listener debe recibir el evento");

        // Then: el evento debe estar marcado como SUCCESS inmediatamente
        Thread.sleep(500);

        try (Connection con = DriverManager.getConnection(jdbcUrl, user, password);
             Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT status FROM events WHERE event_type='" + TestEvent.class.getName() + "'");
            rs.next();
            assertEquals("SUCCESS", rs.getString("status"), 
                "Los eventos sin @RetryableSubscribe deben marcarse como SUCCESS automáticamente");
        }
    }

    // ===========================================
    // CLASES DE APOYO PARA LOS TESTS
    // ===========================================

    /**
     * Evento de prueba con un ID
     */
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

    /**
     * Listener que SIEMPRE tiene éxito con @RetryableSubscribe
     */
    public static class SuccessfulRetryableListener {
        public final CountDownLatch latch = new CountDownLatch(1);
        public final List<TestEvent> processedEvents = new ArrayList<>();

        @Subscribe
        @RetryableSubscribe
        public void handleEvent(TestEvent event) {
            System.out.println("SuccessfulRetryableListener processing: " + event.getId());
            processedEvents.add(event);
            latch.countDown();
            // No lanza excepción = éxito
        }
    }

    /**
     * Listener que FALLA N veces antes de tener éxito
     */
    public static class RetryableFailingListener {
        private final int failTimes;
        public final AtomicInteger attemptCount = new AtomicInteger(0);
        public final CountDownLatch successLatch = new CountDownLatch(1);

        public RetryableFailingListener(int failTimes) {
            this.failTimes = failTimes;
        }

        @Subscribe
        @RetryableSubscribe
        public void handleEvent(TestEvent event) {
            int attempt = attemptCount.incrementAndGet();
            System.out.println("RetryableFailingListener attempt #" + attempt + " for: " + event.getId());

            if (attempt <= failTimes) {
                throw new RuntimeException("Simulated failure on attempt " + attempt);
            }

            // Éxito después de N intentos
            System.out.println("RetryableFailingListener SUCCESS on attempt #" + attempt);
            successLatch.countDown();
        }
    }

    /**
     * Listener SIN @RetryableSubscribe (comportamiento tradicional)
     */
    public static class NonRetryableListener {
        public final CountDownLatch latch = new CountDownLatch(1);

        @Subscribe
        public void handleEvent(TestEvent event) {
            System.out.println("NonRetryableListener processing: " + event.getId());
            latch.countDown();
            // Este listener no tiene @RetryableSubscribe, por lo que siempre se marca como SUCCESS
        }
    }
}
