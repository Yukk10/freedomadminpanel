package dev.freedomadminpanel.velocity;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LiteBansSql extends BanSource {
    private static final Pattern TABLE = Pattern.compile("\\{(history|bans|mutes|warnings|kicks)}");
    private final PluginConfig.Database cfg;
    private final String prefix;
    private final Driver driver = new org.mariadb.jdbc.Driver();
    private final ConnectionPool pool = new ConnectionPool(this::open);

    LiteBansSql(PluginConfig.Database cfg) {
        this.cfg = cfg;
        this.prefix = cfg.tablePrefix == null ? "litebans_" : cfg.tablePrefix.replaceAll("[^A-Za-z0-9_]", "");
    }

    @Override
    String describe() {
        return "база " + this.cfg.address + "/" + this.cfg.database;
    }

    private Connection open() throws SQLException {
        String options = this.cfg.options == null ? "" : this.cfg.options.trim();
        String url = "jdbc:mariadb://" + this.cfg.address + "/" + this.cfg.database + (options.isEmpty() ? "" : "?" + options);
        Properties props = new Properties();
        props.setProperty("user", this.cfg.username == null ? "" : this.cfg.username);
        props.setProperty("password", this.cfg.password == null ? "" : this.cfg.password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "15000");
        Connection connection = this.driver.connect(url, props);
        if (connection == null) {
            throw new SQLException("Неверный адрес базы: " + this.cfg.address);
        } else {
            connection.setReadOnly(true);
            return connection;
        }
    }

    @Override
    protected <T> T query(String sql, BanSource.Body<T> body) throws SQLException {
        Matcher m = TABLE.matcher(sql);
        String resolved = m.replaceAll(r -> this.prefix + r.group(1));

        return this.pool.use(c -> {
            try (PreparedStatement st = c.prepareStatement(resolved)) {
                return body.apply(st);
            }
        });
    }

    @Override
    void close() {
        this.pool.close();
    }
}
