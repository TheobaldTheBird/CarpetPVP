package carpet.mixins;

import net.minecraft.world.level.BaseCommandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BaseCommandBlock.class)
public class BaseCommandBlockMixin
{
    @ModifyVariable(
            method = "setCommand",
            at = @At("HEAD"),
            argsOnly = true
    )
    private String trimWhitespace(String command)
    {
        return command.replaceFirst("\\s+$", "");
    }
}