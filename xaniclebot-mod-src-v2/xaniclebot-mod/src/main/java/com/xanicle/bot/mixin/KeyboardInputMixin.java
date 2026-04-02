package com.xanicle.bot.mixin;

import com.xanicle.bot.BotController;
import com.xanicle.bot.XanicleBotMod;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Targets KeyboardInput but EXTENDS Input so we can access
 * the movement fields directly — no @Shadow needed, which
 * avoids the refmap field-lookup crash on 1.21.1.
 *
 * Because this abstract class extends Input, 'this.movementForward'
 * etc. compile as field accesses on Input. At runtime Mixin merges
 * this into KeyboardInput (which also extends Input), so the fields
 * are resolved correctly through the inheritance chain.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("RETURN"))
    private void xaniclebot_overrideMovement(boolean slowDown, float f, CallbackInfo ci) {
        BotController controller = XanicleBotMod.CONTROLLER;
        if (controller == null || !controller.isActive()) return;

        BotController.BotMovement mov = controller.getDesiredMovement();

        // Write directly to the Input fields we inherit — no @Shadow required
        this.movementForward  = mov.forward;
        this.movementSideways = mov.sideways;
        this.jumping          = mov.jumping;
        this.sneaking         = mov.sneaking;
        this.pressingForward  = mov.forward  >  0.01f;
        this.pressingBack     = mov.forward  < -0.01f;
        this.pressingLeft     = mov.sideways < -0.01f;
        this.pressingRight    = mov.sideways >  0.01f;
    }
}
