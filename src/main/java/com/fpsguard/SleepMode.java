package com.fpsguard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Pencere arka plandayken (odak kaybı) çalışan durum makinesi.
 *
 * FOCUSED -> ENTERING_SLEEP (kısa bekleme, Alt+Tab titremesi için) -> SLEEPING -> FOCUSED
 *
 * Her tick'te yalnızca bir boolean karşılaştırması yapılır; ağır işler yalnızca durum değişiminde çalışır.
 * Hiçbir thread, timer ya da executor oluşturulmaz. Hepsi Minecraft client thread'inde çalışır.
 *
 * Geri yükleme kuralı: bir ayarı yalnızca biz değiştirdiysek VE değeri hâlâ bizim uyguladığımız değerse
 * eski haline döndürürüz. Başka bir mod/kullanıcı arada değiştirdiyse onun değerine dokunmayız.
 */
final class SleepMode {
    enum State { FOCUSED, ENTERING_SLEEP, SLEEPING }

    /** Uykuya geçtikten sonra GC isteği için bekleme (20 tick = 1 sn). */
    private static final int GC_DELAY_TICKS = 100;

    private State state = State.FOCUSED;
    private int unfocusedTicks;
    private boolean gcPending;
    private int gcDelay;

    private boolean renderChanged;
    private int savedRender;
    private int appliedRender;

    private boolean simChanged;
    private int savedSim;
    private int appliedSim;

    private boolean muteChanged;
    private double savedMaster;

    private boolean particlesChanged;
    private ParticleStatus savedParticles = ParticleStatus.ALL;

    private boolean fpsChanged;

    boolean isSleeping() {
        return state == State.SLEEPING;
    }

    /** Her client tick'te çağrılır. */
    void update(Minecraft mc, boolean focused, GuardController owner) {
        if (!FpsGuardConfig.ENABLE_SLEEP.get()) {
            if (state != State.FOCUSED) {
                forceWake(mc, owner);
            }
            return;
        }

        switch (state) {
            case FOCUSED -> {
                if (!focused) {
                    state = State.ENTERING_SLEEP;
                    unfocusedTicks = 0;
                }
            }
            case ENTERING_SLEEP -> {
                if (focused) {
                    state = State.FOCUSED;
                } else if (++unfocusedTicks >= FpsGuardConfig.SLEEP_DELAY.get() * 20) {
                    enter(mc, owner);
                    state = State.SLEEPING;
                }
            }
            case SLEEPING -> {
                if (focused) {
                    wake(mc, owner);
                    state = State.FOCUSED;
                } else if (gcPending && --gcDelay <= 0) {
                    gcPending = false;
                    log("Requesting one garbage collection (not guaranteed by the JVM)");
                    System.gc();
                }
            }
        }
    }

    /** Dünyadan çıkış / oyun kapanışı / özellik kapatma durumlarında güvenli geri dönüş. */
    void forceWake(Minecraft mc, GuardController owner) {
        if (state == State.SLEEPING) {
            wake(mc, owner);
        }
        state = State.FOCUSED;
        gcPending = false;
    }

    private void enter(Minecraft mc, GuardController owner) {
        log("Entering Sleep Mode");
        owner.beforeSleep(mc);

        Options o = mc.options;
        renderChanged = false;
        simChanged = false;
        muteChanged = false;
        particlesChanged = false;
        fpsChanged = false;

        // Mesafeler: yalnızca bir dünyadayken; sunucuda varsayılan olarak dokunma (chunk'lar tekrar inmesin)
        boolean inWorld = mc.level != null;
        boolean distanceAllowed = inWorld && (mc.hasSingleplayerServer() || FpsGuardConfig.DISTANCE_IN_MP.get());

        if (distanceAllowed && FpsGuardConfig.ENABLE_DYNAMIC_RENDER.get()) {
            savedRender = o.renderDistance().get();
            int target = Math.min(savedRender, Math.max(2, FpsGuardConfig.BG_RENDER.get()));
            if (target != savedRender) {
                o.renderDistance().set(target);
                appliedRender = o.renderDistance().get();
                renderChanged = appliedRender != savedRender;
            }
        }

        // Simulation en az 5 olabilir (vanilla sınırı). Varsayılan kapalı: uzaktaki makineler dururdu.
        if (distanceAllowed && mc.hasSingleplayerServer() && FpsGuardConfig.REDUCE_SIM_BG.get()) {
            savedSim = o.simulationDistance().get();
            int target = Math.min(savedSim, Math.max(5, FpsGuardConfig.BG_SIM.get()));
            if (target != savedSim) {
                o.simulationDistance().set(target);
                appliedSim = o.simulationDistance().get();
                simChanged = appliedSim != savedSim;
            }
        }

        if (FpsGuardConfig.ENABLE_AUTO_MUTE.get()) {
            OptionInstance<Double> master = o.getSoundSourceOptionInstance(SoundSource.MASTER);
            savedMaster = master.get();
            // Zaten 0 ise dokunma; öne gelince 0 kalır
            if (savedMaster > 0.0) {
                master.set(0.0);
                muteChanged = master.get() == 0.0;
            }
        }

        if (FpsGuardConfig.ENABLE_PARTICLE_CULL.get()) {
            OptionInstance<ParticleStatus> particles = o.particles();
            savedParticles = particles.get();
            if (savedParticles != ParticleStatus.MINIMAL) {
                particles.set(ParticleStatus.MINIMAL);
                particlesChanged = particles.get() == ParticleStatus.MINIMAL;
            }
        }

        // FPS sınırı doğrudan pencereye uygulanır (options.txt'e yazılmaz, 10'un altına da inebilir)
        if (FpsGuardConfig.ENABLE_BG_FPS.get()) {
            mc.getWindow().setFramerateLimit(Mth.clamp(FpsGuardConfig.BG_FPS.get(), 1, 260));
            fpsChanged = true;
        }

        if (FpsGuardConfig.ENABLE_RAM_CLEANER.get()) {
            gcPending = true;
            gcDelay = GC_DELAY_TICKS;
        }
    }

    private void wake(Minecraft mc, GuardController owner) {
        log("Leaving Sleep Mode");
        Options o = mc.options;
        gcPending = false;

        if (renderChanged) {
            if (o.renderDistance().get() == appliedRender) {
                o.renderDistance().set(savedRender);
            }
            renderChanged = false;
        }
        if (simChanged) {
            if (o.simulationDistance().get() == appliedSim) {
                o.simulationDistance().set(savedSim);
            }
            simChanged = false;
        }
        if (muteChanged) {
            OptionInstance<Double> master = o.getSoundSourceOptionInstance(SoundSource.MASTER);
            if (master.get() == 0.0) {
                master.set(savedMaster);
            }
            muteChanged = false;
        }
        if (particlesChanged) {
            OptionInstance<ParticleStatus> particles = o.particles();
            if (particles.get() == ParticleStatus.MINIMAL) {
                particles.set(savedParticles);
            }
            particlesChanged = false;
        }
        if (fpsChanged) {
            mc.getWindow().setFramerateLimit(o.framerateLimit().get());
            fpsChanged = false;
        }

        owner.afterWake();
    }

    private static void log(String msg) {
        if (FpsGuardConfig.DEBUG.get()) {
            FpsGuard.LOGGER.info("[FpsGuard] {}", msg);
        }
    }
}
