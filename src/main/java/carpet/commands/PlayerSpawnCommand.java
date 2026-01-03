package carpet.commands;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public class PlayerSpawnCommand
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx)
    {
        dispatcher.register(
                literal("playerspawn")
                        .requires(src -> src.hasPermission(2))
                        .then(argument("player", StringArgumentType.word())
                                .suggests((c, b) -> suggest(getNameSuggestions(c.getSource()), b))
                                .executes(PlayerSpawnCommand::spawn)
                                .then(literal("at")
                                        .then(argument("position", Vec3Argument.vec3())
                                                .executes(PlayerSpawnCommand::spawn)
                                                .then(literal("in")
                                                        .then(argument("gamemode", GameModeArgument.gameMode())
                                                                .executes(PlayerSpawnCommand::spawn)
                                                                .then(literal("on")
                                                                        .then(argument("dimension",
                                                                                DimensionArgument.dimension())
                                                                                .executes(PlayerSpawnCommand::spawn)))))))
                        ));
    }

    private static Set<String> getNameSuggestions(CommandSourceStack source)
    {
        Set<String> names = new LinkedHashSet<>();

        names.add("Steve");
        names.add("Alex");
        names.add("TheobaldTheBot");

        names.addAll(source.getOnlinePlayerNames());

        return names;
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        String name = StringArgumentType.getString(context, "player");
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        PlayerList manager = server.getPlayerList();

        if (manager.getPlayerByName(name) != null) return 0;
        if (name.length() > maxNameLength(server)) return 0;

        GameProfile profile = Objects.requireNonNull(server.getProfileCache()).get(name).orElse(null);
        if (profile == null)
        {
            if (!CarpetSettings.allowSpawningOfflinePlayers) return 0;
            profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        }

        Vec3 pos = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("position"))
                ? Vec3Argument.getVec3(context, "position")
                : source.getPosition();

        GameType mode = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("gamemode"))
                ? GameModeArgument.getGameMode(context, "gamemode")
                : GameType.CREATIVE;

        ResourceKey<Level> dim = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("dimension"))
                ? DimensionArgument.getDimension(context, "dimension").dimension()
                : source.getLevel().dimension();

        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) return 0;

        EntityPlayerMPFake.createFake(
                name, server, pos, source.getRotation().y, source.getRotation().x, dim, mode, true
        );
        return 1;
    }

    private static int maxNameLength(MinecraftServer server)
    {
        return server.getPort() >= 0 ? SharedConstants.MAX_PLAYER_NAME_LENGTH : 40;
    }
}
