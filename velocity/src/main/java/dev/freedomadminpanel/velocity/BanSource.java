package dev.freedomadminpanel.velocity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

abstract class BanSource {
    private static final String NOT_REMOVED = "(removed_by_uuid IS NULL OR removed_by_uuid = '#expired' OR removed_by_name = '#expired')";

    protected abstract <T> T query(String sql, BanSource.Body<T> body) throws SQLException;

    abstract String describe();

    List<PlayerDirectory.Known> history() throws SQLException {
        String sql = "SELECT uuid, name, MAX(date) AS last_seen FROM {history} GROUP BY uuid, name";
        return this.query(sql, st -> {
            Map<UUID, PlayerDirectory.Known> byUuid = new HashMap<>();

            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("uuid"));
                    String name = rs.getString("name");
                    Timestamp last = rs.getTimestamp("last_seen");
                    if (uuid != null && name != null && !name.isBlank()) {
                        long seen = last == null ? 0L : last.getTime();
                        PlayerDirectory.Known prev = byUuid.get(uuid);
                        if (prev == null || seen > prev.lastSeen()) {
                            byUuid.put(uuid, new PlayerDirectory.Known(name, uuid, seen));
                        }
                    }
                }
            }

            return new ArrayList<>(byUuid.values());
        });
    }

    UUID uuidByName(String name) throws SQLException {
        return this.query("SELECT uuid FROM {history} WHERE name=? ORDER BY date DESC LIMIT 1", st -> {
            st.setString(1, name);

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? parseUuid(rs.getString(1)) : null;
            }
        });
    }

    Set<UUID> activeBans() throws SQLException {
        return this.active("bans");
    }

    Set<UUID> activeMutes() throws SQLException {
        return this.active("mutes");
    }

    private Set<UUID> active(String table) throws SQLException {
        String sql = "SELECT uuid FROM {" + table + "} WHERE active=TRUE AND (until<=0 OR until>?)";
        return this.query(sql, st -> {
            st.setLong(1, System.currentTimeMillis());
            Set<UUID> result = new HashSet<>();

            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("uuid"));
                    if (uuid != null) {
                        result.add(uuid);
                    }
                }
            }

            return result;
        });
    }

    BanSource.Counts counts(UUID uuid, long since) throws SQLException {
        return new BanSource.Counts(
            this.count(
                "SELECT COUNT(*) FROM {warnings} WHERE uuid=? AND time>=? AND (removed_by_uuid IS NULL OR removed_by_uuid = '#expired' OR removed_by_name = '#expired')",
                uuid,
                since
            ),
            this.count("SELECT COUNT(*) FROM {kicks} WHERE uuid=? AND time>=?", uuid, since),
            this.count(
                "SELECT COUNT(*) FROM {mutes} WHERE uuid=? AND time>=? AND (removed_by_uuid IS NULL OR removed_by_uuid = '#expired' OR removed_by_name = '#expired')",
                uuid,
                since
            ),
            this.count(
                "SELECT COUNT(*) FROM {bans} WHERE uuid=? AND time>=? AND (removed_by_uuid IS NULL OR removed_by_uuid = '#expired' OR removed_by_name = '#expired')",
                uuid,
                since
            )
        );
    }

    int activeWarnings(UUID uuid) throws SQLException {
        return this.query("SELECT COUNT(*) FROM {warnings} WHERE uuid=? AND active=TRUE AND (until<=0 OR until>?)", st -> {
            st.setString(1, uuid.toString());
            st.setLong(2, System.currentTimeMillis());

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    int jailCount(String table, UUID uuid, String name, long since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE (uuid=? OR name=?) AND time>=? AND NOT (released > 0 AND until > 0 AND released < until - 60000)";
        return this.query(sql, st -> {
            st.setString(1, uuid.toString());
            st.setString(2, name);
            st.setLong(3, since);

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    int jailTotal(String table, UUID uuid, String name) throws SQLException {
        return this.query("SELECT COUNT(*) FROM " + table + " WHERE uuid=? OR name=?", st -> {
            st.setString(1, uuid.toString());
            st.setString(2, name);

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    List<BanSource.JailEntry> jailHistory(String table, UUID uuid, String name, int limit, int offset) throws SQLException {
        String sql = "SELECT id, name, jailed_by_name, reason, jail, cell, server, time, until, released FROM "
            + table
            + " WHERE uuid=? OR name=? ORDER BY time DESC LIMIT "
            + limit
            + " OFFSET "
            + offset;
        return this.query(
            sql,
            st -> {
                st.setString(1, uuid.toString());
                st.setString(2, name);
                List<BanSource.JailEntry> list = new ArrayList<>();

                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        int cell = rs.getInt("cell");
                        Integer cellOrNull = rs.wasNull() ? null : cell;
                        list.add(
                            new BanSource.JailEntry(
                                rs.getLong("id"),
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

                return list;
            }
        );
    }

    private int count(String sql, UUID uuid, long since) throws SQLException {
        return this.query(sql, st -> {
            st.setString(1, uuid.toString());
            st.setLong(2, since);

            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    static UUID parseUuid(String s) {
        if (s != null && s.length() >= 32) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    interface Body<T> {
        T apply(PreparedStatement statement) throws SQLException;
    }

    record Counts(int warn, int kick, int mute, int ban) {
    }

    record JailEntry(long id, String name, String jailedBy, String reason, String jail, Integer cell, String server, long time, long until, long released) {
        boolean early() {
            return this.released > 0L && this.until > 0L && this.released < this.until - 60000L;
        }
    }
}
