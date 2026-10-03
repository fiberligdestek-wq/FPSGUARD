package com.fpsguard;

import com.fpsguard.FpsGuardConfig.Corner;
import com.fpsguard.FpsGuardConfig.Profile;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
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

public final class GuardController {
    static final KeyMapping KEY_HUD = new KeyMapping("key.fpsguard.hud", GLFW.GLFW_KEY_F8, "key.categories.fpsguard");
    static final KeyMapping KEY_GUARD = new KeyMapping("key.fpsguard.toggle", GLFW.GLFW_KEY_F9, "key.categories.fpsguard");
    static final KeyMapping KEY_PROFILE = new KeyMapping("key.fpsguard.profile", GLFW.GLFW_KEY_F7, "key.categories.fpsguard");

    private static final int SAMPLE_MAX = 10;
    private static final double ENTITY_STEP = 0.25;

    private final int[] samples = new int[SAMPLE_MAX];
    private int sampleCount;
    private int sampleIdx;

    private int tickCounter;
    private int graceTicks;

    private Boolean hudOn;      // null = config'ten oku
    private Boolean guardOn;    // null = config'ten oku

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
    private int cpuPct = -1;

    private Integer savedFrameLimit;

    public static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(KEY_HUD);
        e.register(KEY_GUARD);
        e.register(KEY_PROFILE);
    }

    private boolean isGuardOn() {
        if (guardOn == null) guardOn = FpsGuardConfig.ENABLED.get();
        return guardOn;
    }

    private boolean isHudOn() {
        if (hudOn == null) hudOn = FpsGuardConfig.SHOW_HUD.get();
        return hudOn;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (KEY_HUD.consumeClick()) {
            hudOn = !isHudOn();
            msg(mc, Component.translatable(hudOn ? "fpsguard.msg.hud_on" : "fpsguard.msg.hud_off"));
        }
        while (KEY_GUARD.consumeClick()) {
            guardOn = !isGuardOn();
            if (!guardOn) {
                restoreAll(mc);
            }
            msg(mc, Component.translatable(guardOn ? "fpsguard.msg.guard_on" : "fpsguard.msg.guard_off"));
        }
        while (KEY_PROFILE.consumeClick()) {
            Profile[] all = Profile.values();
            Profile next = all[(FpsGuardConfig.PROFILE.get().ordinal() + 1) % all.length];
            FpsGuardConfig.PROFILE.set(next);
            FpsGuardConfig.PROFILE.save();
            msg(mc, Component.translatable("fpsguard.msg.profile", next.name()));
        }

        handleBackground(mc);

        if (++tickCounter < 20) return;
        tickCounter = 0;

        if (!isGuardOn() || mc.level == null || mc.player == null) return;

        // Saniyede bir örnek al
        cpuPct = readCpu();
        samples[sampleIdx] = mc.getFps();
        sampleIdx = (sampleIdx + 1) % SAMPLE_MAX;
        if (sampleCount < SAMPLE_MAX) sampleCount++;

        if (graceTicks > 0) {
            graceTicks--;
            return;
        }
        // Menü açıkken, oyun durmuşken ya da pencere arka plandayken karar verme
        if (mc.screen != null || mc.isPaused() || !mc.isWindowActive()) return;

        syncBaseline(mc.options);
        checkRam(mc);
        checkFps(mc);
    }

    /** Oyun sürecinin CPU yükü (%), okunamazsa -1. */
    private static int readCpu() {
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                double d = sun.getProcessCpuLoad();
                return d < 0 ? -1 : (int) Math.round(d * 100.0);
            }
        } catch (Throwable ignored) {
            // CPU ölçümü yoksa sadece FPS/RAM ile devam et
        }
        return -1;
    }

    private boolean cpuHigh() {
        return FpsGuardConfig.CPU_AWARE.get() && cpuPct >= FpsGuardConfig.CPU_THRESHOLD.get();
    }

    // ------------------------------------------------------------ FPS mantığı

    private void checkFps(Minecraft mc) {
        int win = FpsGuardConfig.SAMPLE_SECONDS.get();
        if (sampleCount < win) return;
        long sum = 0;
        for (int k = 1; k <= win; k++) {
            sum += samples[(sampleIdx - k + SAMPLE_MAX * 2) % SAMPLE_MAX];
        }
        double avg = sum / (double) win;

        int target = FpsGuardConfig.TARGET_FPS.get();
        long now = System.currentTimeMillis();
        int max = maxLevel();

        if (avg < target - 3 && level < max && now - lastLevelChange >= FpsGuardConfig.reduceCooldownMs()) {
            int step = cpuHigh() ? 2 : 1;
            setLevel(mc, Math.min(max, level + step), now);
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
        if (ramHighSeconds < 3) return;

        long now = System.currentTimeMillis();
        if (FpsGuardConfig.RAM_CLEANUP.get() && now - lastGc > FpsGuardConfig.GC_COOLDOWN.get() * 1000L) {
            // Çöp toplama iste (ayarlanabilir aralıkla)
            System.gc();
            lastGc = now;
            ramHighSeconds = 0;
        } else if (now - lastLevelChange >= 5000 && level < maxLevel()) {
            // GC'den sonra bile RAM yüksekse gerçekten fazla chunk tutuluyor demektir -> mesafeyi kıs
            setLevel(mc, level + 1, now);
        }
    }

    // ------------------------------------------------------- Ayar uygulama

    private int maxLevel() {
        int r = FpsGuardConfig.ADJUST_RENDER.get() ? Math.max(0, baseRender - FpsGuardConfig.minRender()) : 0;
        int s = FpsGuardConfig.ADJUST_SIM.get() ? Math.max(0, baseSim - FpsGuardConfig.minSim()) : 0;
        double minE = Math.max(0.5, FpsGuardConfig.minEntity());
        int e = FpsGuardConfig.ADJUST_ENTITY.get()
                ? (int) Math.max(0, Math.ceil((baseEntity - minE) / ENTITY_STEP)) : 0;
        return Math.max(r, Math.max(s, e));
    }

    private void setLevel(Minecraft mc, int newLevel, long now) {
        level = Math.max(0, newLevel);
        lastLevelChange = now;
        applyLevel(mc.options);
    }

    private void applyLevel(Options o) {
        int r = baseRender;
        int s = baseSim;
        double e = baseEntity;

        if (FpsGuardConfig.ADJUST_RENDER.get()) {
            r = Math.max(Math.min(FpsGuardConfig.minRender(), baseRender), baseRender - level);
        }
        if (FpsGuardConfig.ADJUST_SIM.get()) {
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
        sampleCount = 0;
        sampleIdx = 0;
        ramHighSeconds = 0;
        restoreFrameLimit(mc);
    }

    // ------------------------------------------------ Arka plan FPS kısıtı

    private void handleBackground(Minecraft mc) {
        if (!FpsGuardConfig.BACKGROUND_THROTTLE.get() || !isGuardOn()) {
            if (savedFrameLimit != null) restoreFrameLimit(mc);
            return;
        }
        boolean active = mc.isWindowActive();
        if (!active && savedFrameLimit == null) {
            int cur = mc.options.framerateLimit().get();
            int bg = Math.max(10, (FpsGuardConfig.BACKGROUND_FPS.get() / 10) * 10);
            if (cur > bg) {
                savedFrameLimit = cur;
                mc.options.framerateLimit().set(bg);
            }
        } else if (active && savedFrameLimit != null) {
            restoreFrameLimit(mc);
        }
    }

    private void restoreFrameLimit(Minecraft mc) {
        if (savedFrameLimit != null) {
            mc.options.framerateLimit().set(savedFrameLimit);
            savedFrameLimit = null;
        }
    }

    // ------------------------------------------------------------ Olaylar

    @SubscribeEvent
    public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        baseKnown = false;
        level = 0;
        sampleCount = 0;
        sampleIdx = 0;
        ramHighSeconds = 0;
        graceTicks = FpsGuardConfig.GRACE_SECONDS.get();
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        restoreAll(Minecraft.getInstance());
    }

    @SubscribeEvent
    public void onShutdown(GameShuttingDownEvent e) {
        restoreAll(Minecraft.getInstance());
    }

    // ---------------------------------------------------------------- HUD

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isHudOn() || mc.options.hideGui || mc.level == null) return;

        List<String> lines = new ArrayList<>();
        int fps = mc.getFps();
        int target = FpsGuardConfig.TARGET_FPS.get();
        int fpsColor = fps >= target - 3 ? 0x55FF55 : (fps >= target * 0.7 ? 0xFFFF55 : 0xFF5555);

        boolean showFps = FpsGuardConfig.HUD_FPS.get();
        if (showFps) {
            lines.add("FPS " + fps);
        }
        if (FpsGuardConfig.HUD_RAM.get()) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            lines.add(String.format("RAM %.1f/%.1f GB (%d%%)",
                    used / 1073741824.0, max / 1073741824.0, Math.round(used * 100.0 / max)));
        }
        if (FpsGuardConfig.HUD_CPU.get() && cpuPct >= 0) {
            lines.add("CPU " + cpuPct + "%");
        }
        if (FpsGuardConfig.HUD_GUARD.get()) {
            Options o = mc.options;
            lines.add(isGuardOn()
                    ? String.format("Guard L%d %s | R%d S%d E%.2f", level,
                            FpsGuardConfig.PROFILE.get().name(),
                            o.renderDistance().get(), o.simulationDistance().get(),
                            o.entityDistanceScaling().get())
                    : "Guard OFF");
        }
        if (lines.isEmpty()) return;

        float s = FpsGuardConfig.HUD_SCALE.get().floatValue();
        int lineH = 10;
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, mc.font.width(l));
        float totalW = maxW * s;
        float totalH = lines.size() * lineH * s;
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
        for (int i = 0; i < lines.size(); i++) {
            int color = (showFps && i == 0) ? fpsColor : 0xFFFFFF;
            g.drawString(mc.font, lines.get(i), 0, i * lineH, color, true);
        }
        g.pose().popPose();
    }

    private static void msg(Minecraft mc, Component c) {
        if (mc.player != null) {
            mc.player.displayClientMessage(c, true);
        }
    }
}
