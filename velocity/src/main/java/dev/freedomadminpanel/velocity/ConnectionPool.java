package dev.freedomadminpanel.velocity;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Небольшой пул соединений: раньше на каждый запрос открывалось новое (запрос рецидива — до 7 подключений к MySQL).
 * Соединения переиспользуются; простаивавшее дольше VALIDATE_AFTER_MS проверяется перед выдачей.
 */
final class ConnectionPool {
    private static final int MAX_IDLE = 4;
    private static final long VALIDATE_AFTER_MS = 30000L;
    private final Opener opener;
    private final Deque<Idle> idle = new ArrayDeque<>();

    ConnectionPool(Opener opener) {
        this.opener = opener;
    }

    <T> T use(Body<T> body) throws SQLException {
        Connection c = this.borrow();
        boolean ok = false;

        try {
            T result = body.apply(c);
            ok = true;
            return result;
        } finally {
            if (ok) {
                this.giveBack(c);
            } else {
                closeQuietly(c);
            }
        }
    }

    private Connection borrow() throws SQLException {
        while (true) {
            Idle entry;
            synchronized (this.idle) {
                entry = this.idle.pollFirst();
            }

            if (entry == null) {
                return this.opener.open();
            }

            if (System.currentTimeMillis() - entry.since < VALIDATE_AFTER_MS || isValid(entry.connection)) {
                return entry.connection;
            }

            closeQuietly(entry.connection);
        }
    }

    private void giveBack(Connection c) {
        synchronized (this.idle) {
            if (this.idle.size() < MAX_IDLE) {
                this.idle.addFirst(new Idle(c, System.currentTimeMillis()));
                return;
            }
        }

        closeQuietly(c);
    }

    void close() {
        synchronized (this.idle) {
            for (Idle entry : this.idle) {
                closeQuietly(entry.connection);
            }

            this.idle.clear();
        }
    }

    private static boolean isValid(Connection c) {
        try {
            return !c.isClosed() && c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
        }
    }

    interface Opener {
        Connection open() throws SQLException;
    }

    interface Body<T> {
        T apply(Connection connection) throws SQLException;
    }

    private record Idle(Connection connection, long since) {
    }
}
