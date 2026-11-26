package dev.freedomadminpanel.paper;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;

final class JailDatabase {
    private final Driver driver = new org.mariadb.jdbc.Driver();
    private final String address;
    private final String database;
    private final String username;
    private final String password;
    private final String options;
    final String table;

    JailDatabase(FileConfiguration cfg) {
        this.address = cfg.getString("database.address", "localhost:3306");
        this.database = cfg.getString("database.database", "litebans");
        this.username = cfg.getString("database.username", "");
        this.password = cfg.getString("database.password", "");
        this.options = cfg.getString("database.options", "");
        this.table = cfg.getString("table", "freedomadminpanel_jails").replaceAll("[^A-Za-z0-9_]", "");
    }

    String describe() {
        return this.address + "/" + this.database + ", таблица " + this.table;
    }

    private Connection open() throws SQLException {
        String url = "jdbc:mariadb://" + this.address + "/" + this.database + (this.options != null && !this.options.isBlank() ? "?" + this.options.trim() : "");
        Properties props = new Properties();
        props.setProperty("user", this.username == null ? "" : this.username);
        props.setProperty("password", this.password == null ? "" : this.password);
        props.setProperty("connectTimeout", "5000");
        Connection c = this.driver.connect(url, props);
        if (c == null) {
            throw new SQLException("Неверный адрес базы: " + this.address);
        } else {
            return c;
        }
    }

    void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `"
            + this.table
            + "` (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,uuid VARCHAR(36) NOT NULL,name VARCHAR(32) NOT NULL,jailed_by_uuid VARCHAR(36) NULL,jailed_by_name VARCHAR(32) NULL,reason VARCHAR(512) NULL,jail VARCHAR(64) NULL,cell INT NULL,server VARCHAR(32) NULL,time BIGINT NOT NULL,until BIGINT NOT NULL,released BIGINT NOT NULL DEFAULT 0,INDEX idx_uuid (uuid), INDEX idx_time (time)) DEFAULT CHARSET=utf8mb4";

        try (
            Connection c = this.open();
            Statement st = c.createStatement();
        ) {
            st.executeUpdate(sql);
        }
    }

    void insert(JailDatabase.JailRecord r) throws SQLException {
        String sql = "INSERT INTO `"
            + this.table
            + "` (uuid, name, jailed_by_uuid, jailed_by_name, reason, jail, cell, server, time, until) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (
            Connection c = this.open();
            PreparedStatement st = c.prepareStatement(sql);
        ) {
            st.setString(1, r.uuid().toString());
            st.setString(2, r.name());
            st.setString(3, r.jailedByUuid() == null ? null : r.jailedByUuid().toString());
            st.setString(4, cut(r.jailedByName(), 32));
            st.setString(5, cut(r.reason(), 512));
            st.setString(6, cut(r.jail(), 64));
            if (r.cell() == null) {
                st.setNull(7, 4);
            } else {
                st.setInt(7, r.cell());
            }

            st.setString(8, cut(r.server(), 32));
            st.setLong(9, r.time());
            st.setLong(10, r.until());
            st.executeUpdate();
        }
    }

    void release(UUID uuid, long time) throws SQLException {
        String sql = "UPDATE `" + this.table + "` SET released = ? WHERE uuid = ? AND released = 0 ORDER BY id DESC LIMIT 1";

        try (
            Connection c = this.open();
            PreparedStatement st = c.prepareStatement(sql);
        ) {
            st.setLong(1, time);
            st.setString(2, uuid.toString());
            st.executeUpdate();
        }
    }

    int total(UUID uuid, String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM `" + this.table + "` WHERE uuid = ? OR name = ?";

        try (
            Connection c = this.open();
            PreparedStatement st = c.prepareStatement(sql);
        ) {
            st.setString(1, uuid == null ? "" : uuid.toString());
            st.setString(2, name);

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    List<JailDatabase.Entry> history(UUID uuid, String name, int limit, int offset) throws SQLException {
        String sql = "SELECT name, jailed_by_name, reason, jail, cell, server, time, until, released FROM `"
            + this.table
            + "` WHERE uuid = ? OR name = ? ORDER BY time DESC LIMIT "
            + limit
            + " OFFSET "
            + offset;
        List<JailDatabase.Entry> list = new ArrayList<>();

        try (
            Connection c = this.open();
            PreparedStatement st = c.prepareStatement(sql);
        ) {
            st.setString(1, uuid == null ? "" : uuid.toString());
            st.setString(2, name);

            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int cell = rs.getInt("cell");
                    Integer cellOrNull = rs.wasNull() ? null : cell;
                    list.add(
                        new JailDatabase.Entry(
                            rs.getString("name"),
                            rs.getString("jailed_by_name"),
                            rs.getString("reason"),
                            rs.getString("jail"),
                            cellOrNull,
                            rs.getString("server"),
                            rs.getLong("time"),
                            rs.getLong("until"),
                            rs.getLong("released")
                        )
                    );
                }
            }
        }

        return list;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        } else {
            return s.length() <= max ? s : s.substring(0, max);
        }
    }

    record Entry(String name, String jailedBy, String reason, String jail, Integer cell, String server, long time, long until, long released) {
        boolean early() {
            return this.released > 0L && this.until > 0L && this.released < this.until - 60000L;
        }
    }

    record JailRecord(
        UUID uuid, String name, UUID jailedByUuid, String jailedByName, String reason, String jail, Integer cell, String server, long time, long until
    ) {
    }
}
