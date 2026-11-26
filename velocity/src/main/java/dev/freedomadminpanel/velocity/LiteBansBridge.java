package dev.freedomadminpanel.velocity;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import litebans.api.Database;

final class LiteBansBridge extends BanSource {
    LiteBansBridge() {
        Database.get();
    }

    @Override
    String describe() {
        return "LiteBans на Velocity";
    }

    @Override
    protected <T> T query(String sql, BanSource.Body<T> body) throws SQLException {
        try (PreparedStatement st = Database.get().prepareStatement(sql)) {
            return (T)body.apply(st);
        }
    }
}
