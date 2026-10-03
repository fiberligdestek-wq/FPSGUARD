package com.fpsguard;

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

    private static final int WINDOW = 4;          // son 4 saniyenin ortalaması
    private static final double ENTITY_STEP = 0.25;

    private final int[] samples = new int[WINDOW];
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

    private Integer savedFrameLimit;

    public static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(KEY_HUD);
        e.register(KEY_GUARD);
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
            msg(mc, hudOn ? "fpsguard.msg.hud_on" : "fpsguard.msg.hud_off");
        }
        while (KEY_GUARD.consumeClick()) {
            guardOn = !isGuardOn();
            if (!guardOn) {
                restoreAll(mc);
            }
            msg(mc, guardOn ? "fpsguard.msg.guard_on" : "fpsguard.msg.guard_off");
        }

        handleBackground(mc);

        if (++tickCounter < 20) return;
        tickCounter = 0;

        if (!isGuardOn() || mc.level == null || mc.player == null) return;

        // Saniyede bir örnek al
        samples[sampleIdx] = mc.getFps();
        sampleIdx = (sampleIdx + 1) % WINDOW;
        if (sampleCount < WINDOW) sampleCount++;

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

    // ------------------------------------------------------------ FPS mantığı

    private void checkFps(Minecraft mc) {
        if (sampleCount < WINDOW) return;
        long sum = 0;
        for (int s : samples) sum += s;
        double avg = sum / (double) WINDOW;

        int target = FpsGuardConfig.TARGET_FPS.get();
        long now = System.currentTimeMillis();

        if (avg < target - 3 && level < maxLevel() && now - lastLevelChange >= 4000) {
            setLevel(mc, level + 1, now);
        } else if (avg > target + FpsGuardConfig.RESTORE_HEADROOM.get()
                && level > 0
                && ramPct < FpsGuardConfig.RAM_THRESHOLD.get() - 10
                && now - lastLevelChange >= 20000) {
            setLevel(mc, level - 1, now);
        }
    }

    // ------------------------------------------------------------ RAM mantığı

    private void checkRam(Minecraft mc) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        ramPct = (int) Math.round(used * 100.0 / rt.maxMemory());

        if (ramPct >= FpsGuardConfig.RAM_THRESHOLD.get()) {
            ramHighSeconds++;
        } else {
            ramHighSeconds = 0;
        }
        if (ramHighSeconds < 3) return;

        long now = System.currentTimeMillis();
        if (FpsGuardConfig.RAM_CLEANUP.get() && now - lastGc > 60_000) {
            // Çöp toplama iste (dakikada en fazla 1 kez)
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
        int r = Math.max(0, baseRender - FpsGuardConfig.MIN_RENDER.get());
        int s = Math.max(0, baseSim - FpsGuardConfig.MIN_SIM.get());
        double minE = Math.max(0.5, FpsGuardConfig.MIN_ENTITY.get());
        int e = (int) Math.max(0, Math.ceil((baseEntity - minE) / ENTITY_STEP));
        return Math.max(r, Math.max(s, e));
    }

    private void setLevel(Minecraft mc, int newLevel, long now) {
        level = Math.max(0, newLevel);
        lastLevelChange = now;
        applyLevel(mc.options);
    }

    private void applyLevel(Options o) {
        int r = Math.max(Math.min(FpsGuardConfig.MIN_RENDER.get(), baseRender), baseRender - level);
        int s = Math.max(Math.min(FpsGuardConfig.MIN_SIM.get(), baseSim), baseSim - level);
        double minE = Math.min(Math.max(0.5, FpsGuardConfig.MIN_ENTITY.get()), baseEntity);
        double e = Math.max(minE, baseEntity - level * ENTITY_STEP);
        e = Math.round(e * 4.0) / 4.0;

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

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        int pct = (int) Math.round(used * 100.0 / max);

        int fps = mc.getFps();
        int target = FpsGuardConfig.TARGET_FPS.get();
        int color = fps >= target - 3 ? 0x55FF55 : (fps >= target * 0.7 ? 0xFFFF55 : 0xFF5555);

        String text = String.format("FPS %d | RAM %.1f/%.1f GB (%d%%) | Guard %s",
                fps, used / 1073741824.0, max / 1073741824.0, pct,
                isGuardOn() ? ("L" + level) : "OFF");

        GuiGraphics g = event.getGuiGraphics();
        g.drawString(mc.font, text, 4, 4, color, true);
    }

    private static void msg(Minecraft mc, String key) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
