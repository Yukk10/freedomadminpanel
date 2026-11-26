package dev.freedomadminpanel.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

public final class PanelNet {
    private static final int PROTOCOL_VERSION = BuildConstants.PROTOCOL;
    private static final byte C_LIST = 1;
    private static final byte C_SKINS = 2;
    private static final byte C_RECORD = 3;
    private static final byte S_LIST = 1;
    private static final byte S_SKINS = 2;
    private static final byte S_DENIED = 3;
    private static final byte S_RECORD = 4;
    private static final long RECORD_REFRESH_MS = 5000L;
    private static final long RESPONSE_TIMEOUT_MS = 4000L;
    private static PanelNet.Status status = PanelNet.Status.UNKNOWN;
    private static String deniedReason = "";
    private static List<PanelNet.NetPlayer> players = List.of();
    private static Map<String, PanelNet.NetPlayer> byName = Map.of();
    private static int onlineCount;
    private static int offlineCount;
    private static int version;
    private static final Map<String, PlayerRecord> records = new HashMap<>();
    private static final Set<String> recordMissing = new HashSet<>();
    private static final Map<String, Long> recordRequestedAt = new HashMap<>();
    private static int requestId;
    private static long requestSentAt;
    private static boolean awaiting;
    private static int joinDelay = -1;
    private static int assemblingId = -1;
    private static List<PanelNet.NetPlayer>[] parts;
    private static int partsReceived;

    private PanelNet() {
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(PanelPayload.ID, PanelPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PanelPayload.ID, PanelPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(PanelPayload.ID, (payload, context) -> receive(payload.data()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            joinDelay = 40;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void reset() {
        status = PanelNet.Status.UNKNOWN;
        deniedReason = "";
        players = List.of();
        byName = Map.of();
        offlineCount = 0;
        onlineCount = 0;
        awaiting = false;
        assemblingId = -1;
        joinDelay = -1;
        records.clear();
        recordMissing.clear();
        recordRequestedAt.clear();
        SkinCache.clear();
        version++;
    }

    private static void tick() {
        if (joinDelay > 0 && --joinDelay == 0) {
            requestList();
        }

        if (awaiting && System.currentTimeMillis() - requestSentAt > 4000L) {
            awaiting = false;
            if (status == PanelNet.Status.UNKNOWN) {
                status = PanelNet.Status.ABSENT;
                version++;
            }
        }

        SkinCache.tick();
    }

    public static void requestList() {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            if (!awaiting || System.currentTimeMillis() - requestSentAt >= 1000L) {
                requestId++;
                send(out -> {
                    out.writeByte(1);
                    out.writeInt(PROTOCOL_VERSION);
                    out.writeInt(requestId);
                });
                awaiting = true;
                requestSentAt = System.currentTimeMillis();
            }
        }
    }

    public static void requestRecord(String name) {
        if (available()) {
            String key = name.toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            Long last = recordRequestedAt.get(key);
            if (last == null || now - last >= 5000L) {
                recordRequestedAt.put(key, now);
                send(out -> {
                    out.writeByte(3);
                    out.writeUTF(name);
                });
            }
        }
    }

    @Nullable
    public static PlayerRecord record(String name) {
        return records.get(name.toLowerCase(Locale.ROOT));
    }

    public static boolean recordMissing(String name) {
        return recordMissing.contains(name.toLowerCase(Locale.ROOT));
    }

    static void requestSkins(List<String> names) {
        send(out -> {
            out.writeByte(2);
            out.writeShort(names.size());

            for (String name : names) {
                out.writeUTF(name);
            }
        });
    }

    private static void send(PanelNet.Writer writer) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                writer.write(new DataOutputStream(bytes));
                ClientPlayNetworking.send(new PanelPayload(bytes.toByteArray()));
            } catch (Exception e) {
                System.err.println("[freedomadminpanel] Не удалось отправить запрос плагину: " + e);
            }
        }
    }

    private static void receive(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte type = in.readByte();
            switch (type) {
                case 1:
                    int id = in.readInt();
                    int part = in.readUnsignedShort();
                    int partCount = in.readUnsignedShort();
                    in.readInt();
                    int n = in.readUnsignedShort();
                    List<PanelNet.NetPlayer> list = new ArrayList<>(n);

                    for (int i = 0; i < n; i++) {
                        String name = in.readUTF();
                        long lastSeen = in.readLong();
                        int flags = in.readUnsignedByte();
                        String server = in.readUTF();
                        list.add(new PanelNet.NetPlayer(name, lastSeen, (flags & 1) != 0, server, (flags & 2) != 0, (flags & 4) != 0));
                    }

                    if (id != assemblingId) {
                        assemblingId = id;
                        parts = new List[partCount];
                        partsReceived = 0;
                    }

                    if (part < parts.length && parts[part] == null) {
                        parts[part] = list;
                        partsReceived++;
                    }

                    if (partsReceived == parts.length) {
                        applyList();
                    }
                    break;
                case 2:
                    int count = in.readUnsignedShort();

                    for (int i = 0; i < count; i++) {
                        String name = in.readUTF();
                        if (in.readBoolean()) {
                            String value = in.readUTF();
                            String signature = in.readUTF();
                            SkinCache.accept(name, value, signature.isEmpty() ? null : signature);
                        }
                    }
                    break;
                case 3:
                    deniedReason = in.readUTF();
                    status = PanelNet.Status.DENIED;
                    awaiting = false;
                    version++;
                    break;
                case 4:
                    String name = in.readUTF();
                    String key = name.toLowerCase(Locale.ROOT);
                    if (in.readBoolean()) {
                        int window = in.readInt();
                        int warn = in.readUnsignedShort();
                        int kick = in.readUnsignedShort();
                        int mute = in.readUnsignedShort();
                        int jail = in.readUnsignedShort();
                        int ban = in.readUnsignedShort();
                        boolean jailTracked = in.readBoolean();
                        float otherWeight = in.readFloat();
                        PlayerRecord.Formula[] f = new PlayerRecord.Formula[3];

                        for (int ix = 0; ix < 3; ix++) {
                            f[ix] = new PlayerRecord.Formula(in.readUTF(), in.readUTF(), in.readUTF());
                        }

                        int activeWarns = -1;
                        int autoBanAt = 0;
                        String autoBanDuration = "";
                        if (in.available() > 0) {
                            activeWarns = in.readUnsignedShort();
                            autoBanAt = in.readUnsignedShort();
                            autoBanDuration = in.readUTF();
                        }

                        records.put(
                            key,
                            new PlayerRecord(
                                name, window, warn, kick, mute, jail, ban, jailTracked, otherWeight, f[0], f[1], f[2], activeWarns, autoBanAt, autoBanDuration
                            )
                        );
                        recordMissing.remove(key);
                    } else {
                        records.remove(key);
                        recordMissing.add(key);
                    }

                    version++;
            }
        } catch (Exception e) {
            System.err.println("[freedomadminpanel] Не удалось разобрать ответ плагина: " + e);
        }
    }

    private static void applyList() {
        List<PanelNet.NetPlayer> all = new ArrayList<>();

        for (List<PanelNet.NetPlayer> p : parts) {
            all.addAll(p);
        }

        Map<String, PanelNet.NetPlayer> map = new HashMap<>(all.size() * 2);
        int on = 0;

        for (PanelNet.NetPlayer p : all) {
            map.put(p.name().toLowerCase(Locale.ROOT), p);
            if (p.online()) {
                on++;
            }
        }

        players = Collections.unmodifiableList(all);
        byName = map;
        onlineCount = on;
        offlineCount = all.size() - on;
        status = PanelNet.Status.AVAILABLE;
        awaiting = false;
        assemblingId = -1;
        version++;
    }

    public static PanelNet.Status status() {
        return status;
    }

    public static boolean available() {
        return status == PanelNet.Status.AVAILABLE;
    }

    public static String deniedReason() {
        return deniedReason;
    }

    public static int version() {
        return version;
    }

    public static List<PanelNet.NetPlayer> players() {
        return players;
    }

    @Nullable
    public static PanelNet.NetPlayer find(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    public static int onlineCount() {
        return onlineCount;
    }

    public static int offlineCount() {
        return offlineCount;
    }

    public record NetPlayer(String name, long lastSeen, boolean online, String server, boolean banned, boolean muted) {
    }

    public static enum Status {
        UNKNOWN,
        AVAILABLE,
        DENIED,
        ABSENT;
    }

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
