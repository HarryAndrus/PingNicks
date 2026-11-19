package dev.teamnicks.mixin;

import dev.teamnicks.NickReplacer;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every Component that gets drawn (chat lines, HUD text, nametags, tab list, scoreboard,
 * and other mods' overlays such as CosmicPings) is turned into glyphs through
 * ClientLanguage#getVisualOrder. Swapping names here only changes what you SEE; the
 * underlying chat message, player list and anything other mods read stay untouched.
 */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    @ModifyVariable(method = "getVisualOrder", at = @At("HEAD"), argsOnly = true)
    private FormattedText teamnicknames$swapNames(FormattedText text) {
        return NickReplacer.apply(text);
    }
}
