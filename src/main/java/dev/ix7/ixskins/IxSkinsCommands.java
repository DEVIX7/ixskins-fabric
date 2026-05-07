package dev.devix7.ixskins;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class IxSkinsCommands {
    private IxSkinsCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            literal("ixskins")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("set")
                    .then(argument("targets", EntityArgumentType.players())
                        .then(literal("player")
                            .then(argument("username", StringArgumentType.word())
                                .executes(context -> setSkin(
                                    context,
                                    EntityArgumentType.getPlayers(context, "targets"),
                                    SkinSource.player(StringArgumentType.getString(context, "username"))
                                ))
                            )
                        )
                        .then(literal("rawurl")
                            .then(argument("url", StringArgumentType.greedyString())
                                .executes(context -> setSkin(
                                    context,
                                    EntityArgumentType.getPlayers(context, "targets"),
                                    SkinSource.rawUrl(StringArgumentType.getString(context, "url"))
                                ))
                            )
                        )
                    )
                )
                .then(literal("clear")
                    .then(argument("targets", EntityArgumentType.players())
                        .executes(context -> clearSkin(context, EntityArgumentType.getPlayers(context, "targets")))
                    )
                )
                .then(literal("sync")
                    .executes(IxSkinsCommands::sync)
                )
        ));
    }

    private static int setSkin(
        CommandContext<ServerCommandSource> context,
        Collection<ServerPlayerEntity> targets,
        SkinSource source
    ) throws CommandSyntaxException {
        String via = source.kind() == SkinSource.Kind.PLAYER ? "nickname" : "url";

        for (ServerPlayerEntity target : targets) {
            ServerSkinState.set(target.getUuid(), source);

            MutableText message = ixskinsPrefix()
                .append(Text.literal(" "))
                .append(target.getName())
                .append(Text.literal(" set skin via " + via + " "))
                .append(Text.literal(source.display()));

            context.getSource().getServer().getPlayerManager().broadcast(message, false);
        }

        ServerSkinState.syncAll(context.getSource().getServer());
        return targets.size();
    }

    private static int clearSkin(
        CommandContext<ServerCommandSource> context,
        Collection<ServerPlayerEntity> targets
    ) throws CommandSyntaxException {
        for (ServerPlayerEntity target : targets) {
            ServerSkinState.clear(target.getUuid());

            MutableText message = ixskinsPrefix()
                .append(Text.literal(" "))
                .append(target.getName())
                .append(Text.literal(" cleared skin"));

            context.getSource().getServer().getPlayerManager().broadcast(message, false);
        }

        ServerSkinState.syncAll(context.getSource().getServer());
        return targets.size();
    }

    private static int sync(CommandContext<ServerCommandSource> context) {
        ServerSkinState.syncAll(context.getSource().getServer());

        MutableText message = ixskinsPrefix()
            .append(Text.literal(" synced to online modded clients."));

        context.getSource().sendFeedback(() -> message, false);
        return 1;
    }

    private static MutableText ixskinsPrefix() {
        return Text.literal("[")
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)))
            .append(styled("I", 0xaa0000))
            .append(styled("X ", 0xc82a0a))
            .append(styled("S", 0xd35415))
            .append(styled("k", 0xde7f1f))
            .append(styled("i", 0xe8a92a))
            .append(styled("n", 0xf3d334))
            .append(styled("s", 0xffff55))
            .append(Text.literal("]").setStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))
            ));
    }   

    private static MutableText styled(String text, int rgb) {
        return Text.literal(text).setStyle(
            Style.EMPTY
                .withBold(true)
                .withItalic(true)
                .withColor(TextColor.fromRgb(rgb))
        );
    }
}