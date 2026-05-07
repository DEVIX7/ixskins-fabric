package dev.devix7.ixskins.mixin;

import dev.devix7.ixskins.client.ClientSkinRegistry;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
    @Inject(method = "method_52810", at = @At("RETURN"), cancellable = true, remap = false)
    private void ixskins$overrideSkin(CallbackInfoReturnable<SkinTextures> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        SkinTextures replacement = ClientSkinRegistry.getOverride(self.getProfile().getId(), cir.getReturnValue());
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
