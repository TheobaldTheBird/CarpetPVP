package carpet.commands;

import carpet.CarpetSettings;
import carpet.utils.CommandHelper;
import carpet.utils.DistanceCalculator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DistanceCommand
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("distance")
                .requires(s -> CommandHelper.canUseCommand(s, CarpetSettings.commandDistance));

        root.then(literal("from")
                .then(argument("fromPos", Vec3Argument.vec3())
                        .then(literal("to")
                                .then(argument("toPos", Vec3Argument.vec3())
                                        .executes(c -> run(
                                                c.getSource(),
                                                Vec3Argument.getVec3(c, "fromPos"),
                                                Vec3Argument.getVec3(c, "toPos"),
                                                0))

                                        .then(literal("e")
                                                .then(argument("exp", IntegerArgumentType.integer(0))
                                                        .executes(c -> run(
                                                                c.getSource(),
                                                                Vec3Argument.getVec3(c, "fromPos"),
                                                                Vec3Argument.getVec3(c, "toPos"),
                                                                IntegerArgumentType.getInteger(c, "exp")))))

                                        .then(literal("horizontal")
                                                .executes(c -> runXZ(
                                                        c.getSource(),
                                                        Vec3Argument.getVec3(c, "fromPos"),
                                                        Vec3Argument.getVec3(c, "toPos"),
                                                        0))
                                                .then(literal("e")
                                                        .then(argument("exp", IntegerArgumentType.integer(0))
                                                                .executes(c -> runXZ(
                                                                        c.getSource(),
                                                                        Vec3Argument.getVec3(c, "fromPos"),
                                                                        Vec3Argument.getVec3(c, "toPos"),
                                                                        IntegerArgumentType.getInteger(c, "exp"))))))

                                        .then(literal("vertical")
                                                .executes(c -> runY(
                                                        c.getSource(),
                                                        Vec3Argument.getVec3(c, "fromPos"),
                                                        Vec3Argument.getVec3(c, "toPos"),
                                                        0))
                                                .then(literal("e")
                                                        .then(argument("exp", IntegerArgumentType.integer(0))
                                                                .executes(c -> runY(
                                                                        c.getSource(),
                                                                        Vec3Argument.getVec3(c, "fromPos"),
                                                                        Vec3Argument.getVec3(c, "toPos"),
                                                                        IntegerArgumentType.getInteger(c, "exp")))))))))

                .then(argument("fromEntity", EntityArgument.entity())
                        .then(literal("to")
                                .then(argument("toPos", Vec3Argument.vec3())
                                        .executes(c -> run(
                                                c.getSource(),
                                                EntityArgument.getEntity(c, "fromEntity").position(),
                                                Vec3Argument.getVec3(c, "toPos"),
                                                0))

                                        .then(literal("e")
                                                .then(argument("exp", IntegerArgumentType.integer(0))
                                                        .executes(c -> run(
                                                                c.getSource(),
                                                                EntityArgument.getEntity(c, "fromEntity").position(),
                                                                Vec3Argument.getVec3(c, "toPos"),
                                                                IntegerArgumentType.getInteger(c, "exp")))))

                                        .then(literal("horizontal")
                                                .executes(c -> runXZ(
                                                        c.getSource(),
                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                        Vec3Argument.getVec3(c, "toPos"),
                                                        0))
                                                .then(literal("e")
                                                        .then(argument("exp", IntegerArgumentType.integer(0))
                                                                .executes(c -> runXZ(
                                                                        c.getSource(),
                                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                                        Vec3Argument.getVec3(c, "toPos"),
                                                                        IntegerArgumentType.getInteger(c, "exp"))))))

                                        .then(literal("vertical")
                                                .executes(c -> runY(
                                                        c.getSource(),
                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                        Vec3Argument.getVec3(c, "toPos"),
                                                        0))
                                                .then(literal("e")
                                                        .then(argument("exp", IntegerArgumentType.integer(0))
                                                                .executes(c -> runY(
                                                                        c.getSource(),
                                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                                        Vec3Argument.getVec3(c, "toPos"),
                                                                        IntegerArgumentType.getInteger(c, "exp")))))))

                                .then(argument("toEntity", EntityArgument.entity())
                                        .executes(c -> run(
                                                c.getSource(),
                                                EntityArgument.getEntity(c, "fromEntity").position(),
                                                EntityArgument.getEntity(c, "toEntity").position(),
                                                0))

                                        .then(literal("e")
                                                .then(argument("exp", IntegerArgumentType.integer(0))
                                                        .executes(c -> run(
                                                                c.getSource(),
                                                                EntityArgument.getEntity(c, "fromEntity").position(),
                                                                EntityArgument.getEntity(c, "toEntity").position(),
                                                                IntegerArgumentType.getInteger(c, "exp")))))

                                        .then(literal("horizontal")
                                                .executes(c -> runXZ(
                                                        c.getSource(),
                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                        EntityArgument.getEntity(c, "toEntity").position(),
                                                        0))
                                                .then(literal("e")
                                                        .then(argument("exp", IntegerArgumentType.integer(0))
                                                                .executes(c -> runXZ(
                                                                        c.getSource(),
                                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                                        EntityArgument.getEntity(c, "toEntity").position(),
                                                                        IntegerArgumentType.getInteger(c, "exp"))))))

                                        .then(literal("vertical")
                                                .executes(c -> runY(
                                                        c.getSource(),
                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                        EntityArgument.getEntity(c, "toEntity").position(),
                                                        0))
                                                .then(literal("e")
                                                        .then(argument("exp", IntegerArgumentType.integer(0))
                                                                .executes(c -> runY(
                                                                        c.getSource(),
                                                                        EntityArgument.getEntity(c, "fromEntity").position(),
                                                                        EntityArgument.getEntity(c, "toEntity").position(),
                                                                        IntegerArgumentType.getInteger(c, "exp"))))))))));


        dispatcher.register(root);
    }

    private static int run(CommandSourceStack source, Vec3 from, Vec3 to, int exp)
    {
        return DistanceCalculator.distance(source, from, to, exp);
    }

    private static int runXZ(CommandSourceStack source, Vec3 from, Vec3 to, int exp)
    {
        Vec3 f = new Vec3(from.x, 0, from.z);
        Vec3 t = new Vec3(to.x, 0, to.z);
        return DistanceCalculator.distance(source, f, t, exp);
    }

    private static int runY(CommandSourceStack source, Vec3 from, Vec3 to, int exp) {
        Vec3 f = new Vec3(0, from.y, 0);
        Vec3 t = new Vec3(0, to.y, 0);
        return DistanceCalculator.distance(source, f, t, exp);
    }
}
