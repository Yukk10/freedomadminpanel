package dev.freedomadminpanel.velocity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class Protocol {
    static final int VERSION = BuildConstants.PROTOCOL;
    static final byte C_LIST = 1;
    static final byte C_SKINS = 2;
    static final byte C_RECORD = 3;
    static final byte S_LIST = 1;
    static final byte S_SKINS = 2;
    static final byte S_DENIED = 3;
    static final byte S_RECORD = 4;
    static final int CHUNK_SIZE = 1500;
    static final int MAX_SKINS = 48;

    private Protocol() {
    }

    static List<byte[]> encodeList(int requestId, List<PlayerDirectory.Row> rows) throws IOException {
        List<byte[]> out = new ArrayList<>();
        int chunks = Math.max(1, (rows.size() + 1500 - 1) / 1500);

        for (int c = 0; c < chunks; c++) {
            List<PlayerDirectory.Row> part = rows.subList(c * 1500, Math.min(rows.size(), (c + 1) * 1500));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(1);
            data.writeInt(requestId);
            data.writeShort(c);
            data.writeShort(chunks);
            data.writeInt(rows.size());
            data.writeShort(part.size());

            for (PlayerDirectory.Row row : part) {
                data.writeUTF(row.name());
                data.writeLong(row.lastSeen());
                data.writeByte((row.online() ? 1 : 0) | (row.banned() ? 2 : 0) | (row.muted() ? 4 : 0));
                data.writeUTF(row.server());
            }

            out.add(bytes.toByteArray());
        }

        return out;
    }

    static byte[] encodeSkins(List<String> names, List<SkinService.Skin> skins) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeByte(2);
        data.writeShort(names.size());

        for (int i = 0; i < names.size(); i++) {
            data.writeUTF(names.get(i));
            SkinService.Skin skin = skins.get(i);
            data.writeBoolean(skin != null);
            if (skin != null) {
                data.writeUTF(skin.value());
                data.writeUTF(skin.signature() == null ? "" : skin.signature());
            }
        }

        return bytes.toByteArray();
    }

    static byte[] encodeRecord(String name, RecordService.Record record, PluginConfig.Recidivism rc, PluginConfig.WarnAutoBan autoBan) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeByte(4);
        data.writeUTF(name);
        data.writeBoolean(record != null);
        if (record != null) {
            data.writeInt(rc.windowDays);
            data.writeShort(record.counts().warn());
            data.writeShort(record.counts().kick());
            data.writeShort(record.counts().mute());
            data.writeShort(record.jails());
            data.writeShort(record.counts().ban());
            data.writeBoolean(record.jailTracked());
            data.writeFloat(0.0F);

            for (PluginConfig.Formula f : new PluginConfig.Formula[]{rc.mute, rc.jail, rc.ban}) {
                data.writeUTF(f.defaultBase);
                data.writeUTF(f.step);
                data.writeUTF(f.max);
            }

            data.writeShort(record.activeWarns());
            data.writeShort(autoBan.enabled ? autoBan.warnings : 0);
            data.writeUTF(autoBan.duration);
        }

        return bytes.toByteArray();
    }

    static byte[] encodeDenied(String reason) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeByte(3);
        data.writeUTF(reason);
        return bytes.toByteArray();
    }
}
