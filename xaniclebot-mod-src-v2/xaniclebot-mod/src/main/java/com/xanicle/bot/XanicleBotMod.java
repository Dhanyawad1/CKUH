package com.xanicle.bot;

import com.xanicle.bot.command.DuelCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XanicleBotMod implements ClientModInitializer {

    public static final String MOD_ID = "xaniclebot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Singleton controller — referenced by the mixin and commands. */
    public static BotController CONTROLLER;

    @Override
    public void onInitializeClient() {
        CONTROLLER = new BotController();

        // Register /duel command
        DuelCommand.register();

        // Hook into the client tick — runs every game tick (20/s) while in-game
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
                CONTROLLER.tick(client);
            }
        });

        LOGGER.info("[XanicleBot] Loaded. Use /duel <player> to start, /duel stop to stop.");
    }
}
