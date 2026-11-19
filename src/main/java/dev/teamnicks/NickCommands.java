package dev.teamnicks;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-only command (never sent to the server):
 *   /nicks add <player> <nickname...>
 *   /nicks remove <player>
 *   /nicks list
 *   /nicks toggle
 *   /nicks reload
 * Named /nicks (not /nick) so it doesn't hide a server's own /nick command.
 */
final class NickCommands {
    private NickCommands() {}

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("nicks")
                        .executes(NickCommands::list)
                        .then(literal("add")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b))
                                        .then(argument("nickname", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String player = StringArgumentType.getString(ctx, "player");
                                                    String nick = StringArgumentType.getString(ctx, "nickname");
                                                    NickConfig.put(player, nick);
                                                    ctx.getSource().sendFeedback(prefix()
                                                            .append(raw(player).withStyle(ChatFormatting.GRAY))
                                                            .append(raw(" will now show as ").withStyle(ChatFormatting.WHITE))
                                                            .append(raw(nick).withStyle(ChatFormatting.AQUA)));
                                                    return 1;
                                                }))))
                        .then(literal("remove")
                                .then(argument("player", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(NickConfig.snapshot().keySet(), b))
                                        .executes(ctx -> {
                                            String player = StringArgumentType.getString(ctx, "player");
                                            boolean removed = NickConfig.remove(player);
                                            ctx.getSource().sendFeedback(prefix().append(raw(removed
                                                    ? "Removed nickname for " + player
                                                    : "No nickname set for " + player)));
                                            return removed ? 1 : 0;
                                        })))
                        .then(literal("list").executes(NickCommands::list))
                        .then(literal("toggle").executes(ctx -> {
                            NickConfig.setEnabled(!NickConfig.isEnabled());
                            ctx.getSource().sendFeedback(prefix().append(raw(NickConfig.isEnabled()
                                    ? "Nicknames ON" : "Nicknames OFF (showing real names)")));
                            return 1;
                        }))
                        .then(literal("reload").executes(ctx -> {
                            NickConfig.load();
                            ctx.getSource().sendFeedback(prefix().append(raw(
                                    "Reloaded " + NickConfig.snapshot().size() + " nickname(s)")));
                            return 1;
                        }))
        ));
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        Map<String, String> all = NickConfig.snapshot();
        if (all.isEmpty()) {
            ctx.getSource().sendFeedback(prefix().append(raw("No nicknames yet. Use /nicks add <player> <nickname>")));
            return 0;
        }
        ctx.getSource().sendFeedback(prefix().append(raw(all.size() + " nickname(s)"
                + (NickConfig.isEnabled() ? "" : " [OFF]") + ":")));
        all.forEach((name, nick) -> ctx.getSource().sendFeedback(raw("  " + name).withStyle(ChatFormatting.GRAY)
                .append(raw(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(raw(nick).withStyle(ChatFormatting.AQUA))));
        return all.size();
    }

    /** Text the replacer must leave alone, otherwise "alt1 -> girlscout" would print "girlscout -> girlscout". */
    private static MutableComponent raw(String s) {
        return Component.literal(s).setStyle(Style.EMPTY.withInsertion(NickReplacer.RAW_MARKER));
    }

    private static MutableComponent prefix() {
        return raw("[Nicks] ").withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
