package com.fpsguard;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Canlı entity çizimini atlar. Yalnızca RENDER katmanına dokunur; entity tick/mantığı çalışmaya devam eder.
 *
 * - Uyku modunda: tüm canlı entity çizimleri atlanır (enableEntityCulling).
 * - Oyun odaktayken: yalnızca foregroundEntityCullDistance > 0 ise ve (ayara göre) Guard mesafeleri kısmışken,
 *   o mesafeden uzaktaki canlılar atlanır. Kendi oyuncun ve baktığın entity asla atlanmaz.
 *
 * Event iptal edilebilir (ICancellableEvent); iptal, entity'nin bu karedeki çiziminin atlanması demektir.
 * Mixin kullanılmaz: NeoForge'un kendi olayı yeterli ve sürümler arası daha güvenli.
 */
final class RenderCulling {
    private final SleepMode sleep;
    private final GuardController guard;

    RenderCulling(SleepMode sleep, GuardController guard) {
        this.sleep = sleep;
        this.guard = guard;
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (sleep.isSleeping()) {
            if (FpsGuardConfig.ENABLE_ENTITY_CULL.get()) {
                event.setCanceled(true);
            }
            return;
        }

        int distance = FpsGuardConfig.FG_CULL_DISTANCE.get();
        if (distance <= 0) {
            return;
        }
        if (FpsGuardConfig.CULL_ONLY_WHEN_ACTIVE.get() && !guard.isReducing()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LivingEntity entity = event.getEntity();
        if (entity == mc.player || entity == mc.getCameraEntity()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        if (entity.distanceToSqr(camera) > (double) distance * distance) {
            event.setCanceled(true);
        }
    }
}
