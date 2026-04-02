package com.xanicle.bot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.xanicle.bot.XanicleBotMod;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/**
 * Registers the /duel command as a client-side command.
 *
 * Usage:
 *   /duel <player>   — Start fighting that player (bot takes control)
 *   /duel stop       — Stop the bot and give back full control
 */
public class DuelCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(
                ClientCommandManager.literal("duel")

                    // /duel stop
                    .then(ClientCommandManager.literal("stop")
                        .executes(ctx -> {
                            if (!XanicleBotMod.CONTROLLER.isActive()) {
                                ctx.getSource().sendFeedback(
                                    Text.literal("§7[XanicleBot] §cBot is not running."));
                                return 0;
                            }
                            XanicleBotMod.CONTROLLER.stop();
                            ctx.getSource().sendFeedback(
                                Text.literal("§7[XanicleBot] §aStopped. You have full control."));
                            return 1;
                        }))

                    // /duel <playerName>
                    .then(ClientCommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            String targetName = StringArgumentType.getString(ctx, "player");

                            if (targetName.equalsIgnoreCase("stop")) {
                                XanicleBotMod.CONTROLLER.stop();
                                ctx.getSource().sendFeedback(
                                    Text.literal("§7[XanicleBot] §aStopped."));
                                return 1;
                            }

                            // Check target is in world
                            var client = ctx.getSource().getClient();
                            if (client.world == null) {
                                ctx.getSource().sendFeedback(
                                    Text.literal("§7[XanicleBot] §cNot in a world."));
                                return 0;
                            }

                            boolean found = client.world.getPlayers().stream()
                                .anyMatch(p -> p.getName().getString()
                                                .equalsIgnoreCase(targetName));

                            if (!found) {
                                ctx.getSource().sendFeedback(Text.literal(
                                    "§7[XanicleBot] §cPlayer §e" + targetName +
                                    " §cnot found in this world."));
                                return 0;
                            }

                            // Resolve to exact name (case-correct)
                            String resolvedName = client.world.getPlayers().stream()
                                .filter(p -> p.getName().getString()
                                              .equalsIgnoreCase(targetName))
                                .findFirst()
                                .map(p -> p.getName().getString())
                                .orElse(targetName);

                            XanicleBotMod.CONTROLLER.start(resolvedName);
                            ctx.getSource().sendFeedback(Text.literal(
                                "§7[XanicleBot] §aFighting §e" + resolvedName +
                                "§a. Type §e/duel stop §ato stop."));
                            return 1;
                        }))

                    // /duel with no args — show status
                    .executes(ctx -> {
                        if (XanicleBotMod.CONTROLLER.isActive()) {
                            ctx.getSource().sendFeedback(Text.literal(
                                "§7[XanicleBot] §aActive — targeting §e" +
                                XanicleBotMod.CONTROLLER.getTargetName() +
                                "§a. Use §e/duel stop §ato stop."));
                        } else {
                            ctx.getSource().sendFeedback(Text.literal(
                                "§7[XanicleBot] §7Idle. Use §e/duel <player> §7to start."));
                        }
                        return 1;
                    })
            );
        });
    }
}
