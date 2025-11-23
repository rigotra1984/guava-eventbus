package com.rigoberto.pr.Repositories;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.rigoberto.pr.Models.StoredEvent;

public class PostgreSQLEventRepository {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public PostgreSQLEventRepository(String jdbcUrl, String user, String password) throws SQLException {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        initSchema();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    private void initSchema() throws SQLException {
        try (Connection con = getConnection();
             Statement st = con.createStatement()) {

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS events (" +
                "    id BIGSERIAL PRIMARY KEY," +
                "    event_type VARCHAR(255) NOT NULL," +
                "    payload TEXT NOT NULL," +
                "    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                "    attempts INT NOT NULL DEFAULT 0," +
                "    max_attempts INT NOT NULL DEFAULT 5," +
                "    next_attempt_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()," +
                "    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()" +
                ")");
        }
    }

    public void saveEvent(String eventType, String payload, int maxAttempts) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                "INSERT INTO events (event_type, payload, max_attempts) " +
                "VALUES (?, ?, ?)")) {

            ps.setString(1, eventType);
            ps.setString(2, payload);
            ps.setInt(3, maxAttempts);
            ps.executeUpdate();
        }
    }

    public List<StoredEvent> fetchPendingEvents(int limit) throws SQLException {
        List<StoredEvent> list = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                "SELECT id, event_type, payload, attempts, max_attempts " +
                "FROM events " +
                "WHERE status='PENDING' AND next_attempt_at <= NOW() " +
                "ORDER BY created_at ASC " +
                "LIMIT ?")) {

            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new StoredEvent(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts")
                ));
            }
        }
        return list;
    }

    public void markAsSuccess(long id) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                "UPDATE events SET status='SUCCESS' " +
                "WHERE id = ?")) {

            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void markAsFailed(long id, int attempts, long backoffMs) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                "UPDATE events " +
                "SET attempts=?, next_attempt_at=NOW() + (? || ' milliseconds')::interval " +
                "WHERE id = ?")) {

            ps.setInt(1, attempts);
            ps.setLong(2, backoffMs);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

}
