package de.leon.trycity.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.leon.trycity.morph.MorphClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sichtfeld (FOV) ohne das Morph-Tempo: Vanilla zoomt mit der Laufgeschwindigkeit
 * rein und raus, und der Server ändert sie beim Morph – als Zombie wäre die Sicht
 * dauernd enger. Hier zählt das Tempo so, als gäbe es den Morph nicht; Sprinten,
 * Speed-Tränke und Fliegen wirken weiter wie gewohnt ({@link MorphClient#speedWithoutMorph}).
 */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {

    @WrapOperation(method = "getFieldOfViewModifier(ZF)F", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double trycity$ohneMorphTempo(AbstractClientPlayer player, Holder<Attribute> attribute, Operation<Double> original) {
        double value = original.call(player, attribute);
        return attribute == Attributes.MOVEMENT_SPEED ? MorphClient.speedWithoutMorph(player, value) : value;
    }
}
