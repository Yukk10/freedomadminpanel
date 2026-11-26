package dev.freedomadminpanel;

import dev.freedomadminpanel.config.FreedomAdminPanelConfig;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Commands {
    private Commands() {
    }

    public static String build(String key, Map<String, String> vars) {
        String result = FreedomAdminPanelConfig.get().command(key);

        for (Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue().trim());
        }

        result = result.replaceAll(" +", " ").trim();

        while (result.startsWith("/")) {
            result = result.substring(1);
        }

        return result;
    }

    public static boolean send(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            if (command.length() > 256) {
                client.player.sendMessage(Text.literal("[freedomadminpanel] Команда слишком длинная (макс. 256 символов)").formatted(Formatting.RED), false);
                return false;
            } else {
                client.getNetworkHandler().sendChatCommand(command);
                return true;
            }
        } else {
            return false;
        }
    }
}
