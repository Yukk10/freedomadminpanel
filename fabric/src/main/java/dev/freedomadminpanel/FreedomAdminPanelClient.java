package dev.freedomadminpanel;

import dev.freedomadminpanel.chat.ChatSelection;
import dev.freedomadminpanel.config.FreedomAdminPanelConfig;
import dev.freedomadminpanel.gui.ModPanelScreen;
import dev.freedomadminpanel.gui.PlayerListScreen;
import dev.freedomadminpanel.gui.RolePickerScreen;
import dev.freedomadminpanel.net.PanelNet;
import dev.freedomadminpanel.spectate.SpectateControlScreen;
import dev.freedomadminpanel.spectate.SpectateHud;
import dev.freedomadminpanel.spectate.SpectateMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class FreedomAdminPanelClient implements ClientModInitializer {
    public static final String MOD_ID = "freedomadminpanel";
    public static final Category CATEGORY = Category.create(Identifier.of("freedomadminpanel", "main"));
    public static KeyBinding openKey;
    public static KeyBinding playersKey;
    public static KeyBinding spectateToggleKey;
    public static KeyBinding spectateNextKey;
    public static KeyBinding spectatePrevKey;
    public static KeyBinding spectateMouseKey;

    public void onInitializeClient() {
        FreedomAdminPanelConfig.load();
        PanelNet.init();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.freedomadminpanel.open", Type.KEYSYM, 93, CATEGORY));
        playersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.freedomadminpanel.players", Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));
        spectateToggleKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.freedomadminpanel.spectate", Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY)
        );
        spectateNextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.freedomadminpanel.spectate_next", Type.KEYSYM, 262, CATEGORY));
        spectatePrevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.freedomadminpanel.spectate_prev", Type.KEYSYM, 263, CATEGORY));
        spectateMouseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.freedomadminpanel.spectate_mouse", Type.KEYSYM, 342, CATEGORY));
        SpectateHud.register();
        ChatSelection.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean canOpen = client.player != null && client.getNetworkHandler() != null && client.currentScreen == null;

            while (openKey.wasPressed()) {
                if (canOpen) {
                    ModPanelScreen panel = new ModPanelScreen();
                    client.setScreen((Screen)(FreedomAdminPanelConfig.get().role() == null ? new RolePickerScreen(panel) : panel));
                    canOpen = false;
                }
            }

            while (playersKey.wasPressed()) {
                if (canOpen) {
                    client.setScreen(new PlayerListScreen(null));
                    canOpen = false;
                }
            }

            tickSpectate(client, canOpen);
        });
    }

    private static void tickSpectate(MinecraftClient client, boolean inGame) {
        while (spectateToggleKey.wasPressed()) {
            if (inGame) {
                SpectateMode.toggle(client);
            }
        }

        while (spectateNextKey.wasPressed()) {
            if (inGame && SpectateMode.active()) {
                SpectateMode.next(client);
            }
        }

        while (spectatePrevKey.wasPressed()) {
            if (inGame && SpectateMode.active()) {
                SpectateMode.prev(client);
            }
        }

        SpectateMode.tick(client);
        boolean held = spectateMouseHeld(client);
        if (!held) {
            SpectateMode.altSuppressed = false;
        }

        if (held && SpectateMode.active() && client.currentScreen == null && client.player != null && !SpectateMode.altSuppressed) {
            client.setScreen(new SpectateControlScreen());
        }
    }

    public static boolean spectateMouseHeld(MinecraftClient client) {
        Key key = KeyBindingHelper.getBoundKeyOf(spectateMouseKey);
        if (key == null || key.getCode() == InputUtil.UNKNOWN_KEY.getCode()) {
            return false;
        } else {
            return key.getCategory() == Type.MOUSE
                ? GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key.getCode()) == 1
                : InputUtil.isKeyPressed(client.getWindow(), key.getCode());
        }
    }
}
