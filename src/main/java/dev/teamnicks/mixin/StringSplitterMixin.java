package dev.teamnicks.mixin;

import dev.teamnicks.NickReplacer;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Measure text with the nickname too, so the ping label's background box is sized
 * for what is actually drawn. Only applies where the name is actually swapped.
 */
@Mixin(StringSplitter.class)
public abstract class StringSplitterMixin {
    @ModifyVariable(
            method = "stringWidth(Lnet/minecraft/network/chat/FormattedText;)F",
            at = @At("HEAD"), argsOnly = true)
    private FormattedText teamnicknames$swapNamesForWidth(FormattedText text) {
        return NickReplacer.apply(text);
    }

    @ModifyVariable(
            method = "stringWidth(Ljava/lang/String;)F",
            at = @At("HEAD"), argsOnly = true)
    private String teamnicknames$swapNamesForPlainWidth(String text) {
        return NickReplacer.applyPlain(text);
    }
}
