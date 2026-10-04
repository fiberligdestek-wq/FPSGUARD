package com.fpsguard;

import com.fpsguard.FpsGuardConfig.Corner;
import com.fpsguard.FpsGuardConfig.Profile;
import java.lang.management.ManagementFactory;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Oyun odaktayken çalışan Guard: FPS / RAM / CPU durumuna göre mesafeleri kademeli kısar ve geri açar.
 * Uyku modu (arka plan) SleepMode sınıfındadır; bu sınıf onunla odak kaybında el değiştirir.
 *
 * Ağır işler saniyede bir (20 tick) yapılır. Her tick yalnızca tuş kontrolü ve bir boolean karşılaştırması vardır.
 * Gösterge metinleri saniyede bir hazırlanır; kare başına string üretilmez.
 */
public final class GuardController {
    static final KeyMapping KEY_HUD = new KeyMapping("key.fpsguard.hud", GLFW.GLFW_KEY_F8, "key.categories.fpsguard");
    static final KeyMapping KEY_GUARD = new KeyMapping("key.fpsguard.toggle", GLFW.GLFW_KEY_F9, "key.categories.fpsguard");
    static final KeyMapping KEY_PROFILE = new KeyMapping("key.fpsguard.profile", GLFW.GLFW_KEY_F7, "key.categories.fpsguard");

    private static final int SAMPLE_MAX = 10;
    private static final double ENTITY_STEP = 0.25;

    private final SleepMode sleep;

    private final int[] samples = new int[SAMPLE_MAX];
    private int sampleCount;
    private int sampleIdx;
    private int lowStreak;

    private int tickCounter;
    private int graceTicks;
    private boolean wasGuardOn = true;
    private boolean localServer;

    // Kullanıcının kendi ayarları (taban değerler)
    private boolean baseKnown;
    private int baseRender;
    private int baseSim;
    private double baseEntity;
    // Guard'ın en son uyguladığı değerler (kullanıcı elle değiştirdi mi anlamak için)
    private int lastRender;
    private int lastSim;
    private double lastEntity;
    private int level;          // 0 = kullanıcının ayarı

    private long lastLevelChange;
    private long lastGc;
    private int ramHighSeconds;
    private int ramPct;

    private com.sun.management.OperatingSystemMXBean cpuBean;
    private boolean cpuInit;
    private int cpuPct = -1;

    // Gösterge önbelleği (saniyede bir yenilenir)
    private final String[] hudLines = new String[4];
    private int hudCount;
    private int hudWidth;
    private int hudFpsColor = 0xFFFFFF;
    private boolean hudFirstIsFps;

    GuardController(SleepMode sleep) {
        this.sleep = sleep;
    }

    public static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(KEY_HUD);
        e.register(KEY_GUARD);
        e.register(KEY_PROFILE);
    }

    /** Guard şu an mesafeleri kısmış durumda mı? (RenderCulling kullanır) */
    boolean isReducing() {
        return level > 0;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }

        handleKeys(mc);
        sleep.update(mc, mc.isWindowActive(), this);

        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        onSecond(mc);
    }

    private void onSecond(Minecraft mc) {
        boolean on = FpsGuardConfig.ENABLED.get();
        if (!on && wasGuardOn) {
            restoreAll(mc);
        }
        wasGuardOn = on;

        cpuPct = readCpu();

        boolean inWorld = mc.level != null && mc.player != null;
        if (inWorld) {
            localServer = mc.hasSingleplayerServer();
            refreshHud(mc, on);
        } else {
            hudCount = 0;
        }
        if (!on || !inWorld) {
            return;
        }

        // Uykudayken ya da pencere odakta değilken FPS düşük görünür; örnek alma, karar verme
        if (!mc.isWindowActive() || sleep.isSleeping()) {
            return;
        }

        samples[sampleIdx] = mc.getFps();
        sampleIdx = (sampleIdx + 1) % SAMPLE_MAX;
        if (sampleCount < SAMPLE_MAX) {
            sampleCount++;
        }

        if (graceTicks > 0) {
            graceTicks--;
            return;
        }
        // Menü açıkken ya da oyun durmuşken karar verme
        if (mc.screen != null || mc.isPaused()) {
            return;
        }

        syncBaseline(mc.options);
        checkRam(mc);
        checkFps(mc);
    }

    // ------------------------------------------------------------ tuşlar

    private void handleKeys(Minecraft mc) {
        while (KEY_HUD.consumeClick()) {
            boolean on = !FpsGuardConfig.SHOW_HUD.get();
            FpsGuardConfig.SHOW_HUD.set(on);
            FpsGuardConfig.SHOW_HUD.save();
            tickCounter = 19;   // göstergeyi hemen yenile
            msg(mc, Component.translatable(on ? "fpsguard.msg.hud_on" : "fpsguard.msg.hud_off"));
        }
        while (KEY_GUARD.consumeClick()) {
            boolean on = !FpsGuardConfig.ENABLED.get();
            FpsGuardConfig.ENABLED.set(on);
            FpsGuardConfig.ENABLED.save();
            wasGuardOn = on;
            if (!on) {
                restoreAll(mc);
            }
            tickCounter = 19;
            msg(mc, Component.translatable(on ? "fpsguard.msg.guard_on" : "fpsguard.msg.guard_off"));
        }
        while (KEY_PROFILE.consumeClick()) {
            Profile[] all = Profile.values();
            Profile next = all[(FpsGuardConfig.PROFILE.get().ordinal() + 1) % all.length];
            FpsGuardConfig.PROFILE.set(next);
            FpsGuardConfig.PROFILE.save();
            msg(mc, Component.translatable("fpsguard.msg.profile", next.name()));
        }
    }

    // ------------------------------------------------------------ CPU

    /** Oyun sürecinin CPU yükü (%), JVM desteklemiyorsa -1. */
    private int readCpu() {
        if (!cpuInit) {
            cpuInit = true;
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                cpuBean = sun;
            } else {
                FpsGuard.LOGGER.warn("[FpsGuard] CPU load is not available on this JVM; CPU features are disabled");
            }
        }
        if (cpuBean == null) {
            return -1;
        }
        double d = cpuBean.getProcessCpuLoad();
        return d < 0 ? -1 : (int) Math.round(d * 100.0);
    }

    private boolean cpuHigh() {
        return FpsGuardConfig.CPU_AWARE.get() && cpuPct >= FpsGuardConfig.CPU_THRESHOLD.get();
    }

    // ------------------------------------------------------------ FPS mantığı

    private void checkFps(Minecraft mc) {
        long now = System.currentTimeMillis();
        int target = FpsGuardConfig.TARGET_FPS.get();
        int max = maxLevel();
        int last = samples[(sampleIdx - 1 + SAMPLE_MAX) % SAMPLE_MAX];

        // Ani düşüş (savaş / kalabalık / yoğun chunk yükü): üst üste 2 sn çok düşükse 2-3 kademe birden kıs
        int emergencyBelow = target * FpsGuardConfig.emergencyPercent() / 100;
        if (last < emergencyBelow) {
            lowStreak++;
        } else {
            lowStreak = 0;
        }
        if (sampleCount >= 2 && lowStreak >= 2 && level < max && now - lastLevelChange >= 2000) {
            lowStreak = 0;
            setLevel(mc, Math.min(max, level + (cpuHigh() ? 3 : 2)), now);
            return;
        }

        int win = FpsGuardConfig.SAMPLE_SECONDS.get();
        if (sampleCount < win) {
            return;
        }
        long sum = 0;
        for (int k = 1; k <= win; k++) {
            sum += samples[(sampleIdx - k + SAMPLE_MAX * 2) % SAMPLE_MAX];
        }
        double avg = sum / (double) win;

        if (avg < target - 3 && level < max && now - lastLevelChange >= FpsGuardConfig.reduceCooldownMs()) {
            setLevel(mc, Math.min(max, level + (cpuHigh() ? 2 : 1)), now);
        } else if (avg > target + FpsGuardConfig.RESTORE_HEADROOM.get()
                && level > 0
                && !cpuHigh()
                && ramPct < FpsGuardConfig.ramThreshold() - 10
                && now - lastLevelChange >= FpsGuardConfig.restoreCooldownMs()) {
            setLevel(mc, level - 1, now);
        }
    }

    // ------------------------------------------------------------ RAM mantığı

    private void checkRam(Minecraft mc) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        ramPct = (int) Math.round(used * 100.0 / rt.maxMemory());

        if (ramPct >= FpsGuardConfig.ramThreshold()) {
            ramHighSeconds++;
        } else {
            ramHighSeconds = 0;
        }
        if (ramHighSeconds < 3) {
            return;
        }

        long now = System.currentTimeMillis();
        if (FpsGuardConfig.RAM_CLEANUP.get() && now - lastGc > FpsGuardConfig.GC_COOLDOWN.get() * 1000L) {
            // Çöp toplama iste (aralık ayarlı). JVM garanti etmez.
            System.gc();
            lastGc = now;
            ramHighSeconds = 0;
        } else if (now - lastLevelChange >= 5000 && level < maxLevel()) {
            // GC'den sonra bile heap yüksekse gerçekten fazla chunk tutuluyor demektir -> mesafeyi kıs
            setLevel(mc, level + 1, now);
        }
    }

    // ------------------------------------------------------- Ayar uygulama

    private boolean adjustRenderNow() {
        return FpsGuardConfig.ADJUST_RENDER.get()
                && (localServer || FpsGuardConfig.ADJUST_RENDER_ON_SERVERS.get());
    }

    /** Sunucuda client'ın simulation değeri kullanılmaz, o yüzden yalnızca tek oyunculuda. */
    private boolean adjustSimNow() {
        return FpsGuardConfig.ADJUST_SIM.get() && localServer;
    }

    private int maxLevel() {
        int r = adjustRenderNow() ? Math.max(0, baseRender - FpsGuardConfig.minRender()) : 0;
        int s = adjustSimNow() ? Math.max(0, baseSim - FpsGuardConfig.minSim()) : 0;
        double minE = Math.max(0.5, FpsGuardConfig.minEntity());
        int e = FpsGuardConfig.ADJUST_ENTITY.get()
                ? (int) Math.max(0, Math.ceil((baseEntity - minE) / ENTITY_STEP)) : 0;
        return Math.max(r, Math.max(s, e));
    }

    private void setLevel(Minecraft mc, int newLevel, long now) {
        int old = level;
        level = Math.max(0, newLevel);
        lastLevelChange = now;
        applyLevel(mc.options);
        if (FpsGuardConfig.DEBUG.get()) {
            FpsGuard.LOGGER.info("[FpsGuard] Level {} -> {} (render={}, sim={}, entity={})",
                    old, level, lastRender, lastSim, lastEntity);
        }
    }

    private void applyLevel(Options o) {
        int r = baseRender;
        int s = baseSim;
        double e = baseEntity;

        if (adjustRenderNow()) {
            r = Math.max(Math.min(FpsGuardConfig.minRender(), baseRender), baseRender - level);
        }
        if (adjustSimNow()) {
            s = Math.max(Math.min(FpsGuardConfig.minSim(), baseSim), baseSim - level);
        }
        if (FpsGuardConfig.ADJUST_ENTITY.get()) {
            double minE = Math.min(Math.max(0.5, FpsGuardConfig.minEntity()), baseEntity);
            e = Math.max(minE, baseEntity - level * ENTITY_STEP);
            e = Math.round(e * 4.0) / 4.0;
        }

        if (o.renderDistance().get() != r) o.renderDistance().set(r);
        if (o.simulationDistance().get() != s) o.simulationDistance().set(s);
        if (Math.abs(o.entityDistanceScaling().get() - e) > 1e-6) o.entityDistanceScaling().set(e);

        lastRender = o.renderDistance().get();
        lastSim = o.simulationDistance().get();
        lastEntity = o.entityDistanceScaling().get();
    }

    /** Kullanıcı ayarları elle değiştirdiyse yeni değerleri taban kabul et. */
    private void syncBaseline(Options o) {
        int r = o.renderDistance().get();
        int s = o.simulationDistance().get();
        double e = o.entityDistanceScaling().get();
        if (!baseKnown || r != lastRender || s != lastSim || Math.abs(e - lastEntity) > 1e-6) {
            baseKnown = true;
            baseRender = r;
            baseSim = s;
            baseEntity = e;
            lastRender = r;
            lastSim = s;
            lastEntity = e;
            level = 0;
        }
    }

    /** Kullanıcının orijinal ayarlarını geri yükle (dünyadan çıkış / kapanış / guard kapatma). */
    private void restoreAll(Minecraft mc) {
        if (baseKnown && level > 0) {
            level = 0;
            applyLevel(mc.options);
        }
        baseKnown = false;
        resetSamples();
    }

    private void resetSamples() {
        sampleCount = 0;
        sampleIdx = 0;
        lowStreak = 0;
        ramHighSeconds = 0;
    }

    // ------------------------------------------------ Uyku moduyla el değiştirme

    /** Uykuya geçmeden önce: Guard kendi kıstıklarını geri açar, taban değerler uykunun anlık görüntüsüne girer. */
    void beforeSleep(Minecraft mc) {
        restoreAll(mc);
    }

    /** Uykudan çıkınca: taban değerler yeniden okunur, FPS örnekleri sıfırdan toplanır. */
    void afterWake() {
        baseKnown = false;
        resetSamples();
        graceTicks = Math.max(graceTicks, 3);
    }

    // ------------------------------------------------------------ Olaylar

    @SubscribeEvent
    public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        baseKnown = false;
        level = 0;
        resetSamples();
        graceTicks = FpsGuardConfig.GRACE_SECONDS.get();
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        Minecraft mc = Minecraft.getInstance();
        sleep.forceWake(mc, this);
        restoreAll(mc);
        hudCount = 0;
    }

    @SubscribeEvent
    public void onShutdown(GameShuttingDownEvent e) {
        Minecraft mc = Minecraft.getInstance();
        sleep.forceWake(mc, this);
        restoreAll(mc);
    }

    // ---------------------------------------------------------------- HUD

    /** Saniyede bir çağrılır: gösterge metinlerini hazırlar. Kare başına allocation yoktur. */
    private void refreshHud(Minecraft mc, boolean guardOn) {
        hudCount = 0;
        hudFirstIsFps = false;
        if (!FpsGuardConfig.SHOW_HUD.get()) {
            return;
        }

        int fps = mc.getFps();
        int target = FpsGuardConfig.TARGET_FPS.get();
        hudFpsColor = fps >= target - 3 ? 0x55FF55 : (fps >= target * 0.7 ? 0xFFFF55 : 0xFF5555);

        if (FpsGuardConfig.HUD_FPS.get()) {
            hudLines[hudCount++] = "FPS " + fps;
            hudFirstIsFps = true;
        }
        if (FpsGuardConfig.HUD_RAM.get()) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            hudLines[hudCount++] = String.format("RAM %.1f/%.1f GB (%d%%)",
                    used / 1073741824.0, max / 1073741824.0, Math.round(used * 100.0 / max));
        }
        if (FpsGuardConfig.HUD_CPU.get() && cpuPct >= 0) {
            hudLines[hudCount++] = "CPU " + cpuPct + "%";
        }
        if (FpsGuardConfig.HUD_GUARD.get()) {
            Options o = mc.options;
            hudLines[hudCount++] = guardOn
                    ? String.format("Guard L%d %s | R%d S%d E%.2f", level,
                            FpsGuardConfig.PROFILE.get().name(),
                            o.renderDistance().get(), o.simulationDistance().get(),
                            o.entityDistanceScaling().get())
                    : "Guard OFF";
        }

        int w = 0;
        for (int i = 0; i < hudCount; i++) {
            w = Math.max(w, mc.font.width(hudLines[i]));
        }
        hudWidth = w;
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (hudCount == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null) {
            return;
        }

        float s = FpsGuardConfig.HUD_SCALE.get().floatValue();
        int lineH = 10;
        float totalW = hudWidth * s;
        float totalH = hudCount * lineH * s;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Corner corner = FpsGuardConfig.HUD_CORNER.get();
        boolean right = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT;
        boolean bottom = corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT;
        float x = right ? sw - 4 - totalW : 4;
        float y = bottom ? sh - 4 - totalH : 4;

        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(s, s, 1f);
        for (int i = 0; i < hudCount; i++) {
            int color = (hudFirstIsFps && i == 0) ? hudFpsColor : 0xFFFFFF;
            g.drawString(mc.font, hudLines[i], 0, i * lineH, color, true);
        }
        g.pose().popPose();
    }

    private static void msg(Minecraft mc, Component c) {
        if (mc.player != null) {
            mc.player.displayClientMessage(c, true);
        }
    }
}
