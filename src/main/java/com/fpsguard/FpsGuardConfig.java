package com.fpsguard;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tüm ayarlar. Oyun içinde: Mods -> FPS Guard -> Config. Değişiklikler anında uygulanır. */
public final class FpsGuardConfig {
    public enum Profile { LIGHT, BALANCED, AGGRESSIVE, EXTREME, CUSTOM }

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---- genel
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.EnumValue<Profile> PROFILE;
    public static final ModConfigSpec.BooleanValue DEBUG;

    // ---- guard (oyun odaktayken dinamik mesafe)
    public static final ModConfigSpec.IntValue TARGET_FPS;
    public static final ModConfigSpec.IntValue SAMPLE_SECONDS;
    public static final ModConfigSpec.IntValue GRACE_SECONDS;
    public static final ModConfigSpec.BooleanValue ADJUST_RENDER;
    public static final ModConfigSpec.BooleanValue ADJUST_SIM;
    public static final ModConfigSpec.BooleanValue ADJUST_ENTITY;
    public static final ModConfigSpec.BooleanValue ADJUST_RENDER_ON_SERVERS;
    public static final ModConfigSpec.IntValue MIN_RENDER;
    public static final ModConfigSpec.IntValue MIN_SIM;
    public static final ModConfigSpec.DoubleValue MIN_ENTITY;
    public static final ModConfigSpec.IntValue REDUCE_COOLDOWN;
    public static final ModConfigSpec.IntValue RESTORE_COOLDOWN;
    public static final ModConfigSpec.IntValue RESTORE_HEADROOM;
    public static final ModConfigSpec.IntValue EMERGENCY_PERCENT;

    // ---- ram
    public static final ModConfigSpec.IntValue RAM_THRESHOLD;
    public static final ModConfigSpec.BooleanValue RAM_CLEANUP;
    public static final ModConfigSpec.IntValue GC_COOLDOWN;

    // ---- cpu
    public static final ModConfigSpec.BooleanValue CPU_AWARE;
    public static final ModConfigSpec.IntValue CPU_THRESHOLD;

    // ---- sleep (pencere arka plandayken)
    public static final ModConfigSpec.BooleanValue ENABLE_SLEEP;
    public static final ModConfigSpec.IntValue SLEEP_DELAY;
    public static final ModConfigSpec.BooleanValue ENABLE_BG_FPS;
    public static final ModConfigSpec.IntValue BG_FPS;
    public static final ModConfigSpec.BooleanValue ENABLE_DYNAMIC_RENDER;
    public static final ModConfigSpec.IntValue BG_RENDER;
    public static final ModConfigSpec.BooleanValue REDUCE_SIM_BG;
    public static final ModConfigSpec.IntValue BG_SIM;
    public static final ModConfigSpec.BooleanValue DISTANCE_IN_MP;
    public static final ModConfigSpec.BooleanValue ENABLE_AUTO_MUTE;
    public static final ModConfigSpec.BooleanValue ENABLE_PARTICLE_CULL;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_CULL;
    public static final ModConfigSpec.BooleanValue ENABLE_RAM_CLEANER;

    // ---- culling (oyun odaktayken)
    public static final ModConfigSpec.IntValue FG_CULL_DISTANCE;
    public static final ModConfigSpec.BooleanValue CULL_ONLY_WHEN_ACTIVE;

    // ---- hud
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.EnumValue<Corner> HUD_CORNER;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.BooleanValue HUD_FPS;
    public static final ModConfigSpec.BooleanValue HUD_RAM;
    public static final ModConfigSpec.BooleanValue HUD_CPU;
    public static final ModConfigSpec.BooleanValue HUD_GUARD;

    public static final ModConfigSpec SPEC;

    private static ModConfigSpec.Builder c(String name, String comment) {
        return B.comment(comment).translation("fpsguard.configuration." + name);
    }

    private static void section(String name, String comment) {
        B.comment(comment).translation("fpsguard.configuration." + name).push(name);
    }

    static {
        section("general", "Genel ayarlar.");
        ENABLED = c("enabled", "FPS Guard açık mı? (oyun içinde F9)").define("enabled", true);
        PROFILE = c("profile",
                "Profil (oyun içinde F7). LIGHT=az müdahale, BALANCED=dengeli, AGGRESSIVE=sert, EXTREME=en sert, CUSTOM=sadece senin değerlerin.")
                .defineEnum("profile", Profile.BALANCED);
        DEBUG = c("debugLogging", "Durum değişimlerini log'a yaz (uyku girişi/çıkışı, kademe değişimi). Her tick log yazmaz.")
                .define("debugLogging", false);
        B.pop();

        section("guard", "Oyun odaktayken FPS'e göre mesafeleri otomatik kısma / geri açma.");
        TARGET_FPS = c("targetFps", "Hedef FPS. Ortalama FPS bunun altına inerse mesafeler kısılır.")
                .defineInRange("targetFps", 60, 20, 240);
        SAMPLE_SECONDS = c("sampleSeconds", "FPS ortalaması kaç saniyeye bakılarak hesaplansın.")
                .defineInRange("sampleSeconds", 4, 2, 10);
        GRACE_SECONDS = c("joinGraceSeconds",
                "Dünyaya girince bu kadar saniye ayar değiştirme (chunk yüklerken FPS zaten düşük olur).")
                .defineInRange("joinGraceSeconds", 20, 0, 120);
        ADJUST_RENDER = c("adjustRender", "Render mesafesini kıs.").define("adjustRender", true);
        ADJUST_SIM = c("adjustSimulation",
                "Simulation mesafesini kıs (yalnızca tek oyunculu; sunucuda client değeri kullanılmaz).")
                .define("adjustSimulation", true);
        ADJUST_ENTITY = c("adjustEntity", "Entity çizim mesafesini kıs.").define("adjustEntity", true);
        ADJUST_RENDER_ON_SERVERS = c("adjustRenderOnServers",
                "Çok oyunculu sunucuda da render mesafesini kıs (her değişim chunk'ları yeniden yükletebilir).")
                .define("adjustRenderOnServers", true);
        MIN_RENDER = c("minRenderDistance", "En düşük render mesafesi (chunk).")
                .defineInRange("minRenderDistance", 6, 2, 32);
        MIN_SIM = c("minSimulationDistance", "En düşük simulation mesafesi (Minecraft'ta en az 5).")
                .defineInRange("minSimulationDistance", 5, 5, 32);
        MIN_ENTITY = c("minEntityDistanceScale", "En düşük entity mesafe çarpanı (0.5 = %50).")
                .defineInRange("minEntityDistanceScale", 0.5, 0.5, 5.0);
        REDUCE_COOLDOWN = c("reduceCooldownSeconds", "İki kısma arasındaki en az süre (sn).")
                .defineInRange("reduceCooldownSeconds", 4, 1, 30);
        RESTORE_COOLDOWN = c("restoreCooldownSeconds", "İki geri açma arasındaki en az süre (sn).")
                .defineInRange("restoreCooldownSeconds", 20, 5, 120);
        RESTORE_HEADROOM = c("restoreHeadroomFps",
                "FPS, hedefin bu kadar üstüne çıkarsa kısılan mesafeler 1 kademe geri açılır.")
                .defineInRange("restoreHeadroomFps", 30, 10, 200);
        EMERGENCY_PERCENT = c("emergencyFpsPercent",
                "Ani düşüş: üst üste 2 saniye FPS, hedefin bu yüzdesinin altındaysa 2 kademe birden kısılır (savaş / kalabalık anları).")
                .defineInRange("emergencyFpsPercent", 50, 10, 90);
        B.pop();

        section("ram", "Java heap (RAM) önlemleri.");
        RAM_THRESHOLD = c("ramThresholdPercent",
                "Java heap kullanımı bu yüzdeyi 3 sn aşarsa RAM önlemleri devreye girer.")
                .defineInRange("ramThresholdPercent", 88, 60, 98);
        RAM_CLEANUP = c("ramCleanup", "Heap dolunca çöp toplama (GC) iste. (JVM garanti etmez.)")
                .define("ramCleanup", true);
        GC_COOLDOWN = c("gcCooldownSeconds", "İki GC isteği arasındaki en az süre (sn).")
                .defineInRange("gcCooldownSeconds", 60, 20, 600);
        B.pop();

        section("cpu", "CPU'ya duyarlı davranış.");
        CPU_AWARE = c("cpuAware",
                "Oyunun CPU yükü yüksekse ve FPS düşükse mesafeyi daha hızlı kıs, CPU yüksekken geri açma.")
                .define("cpuAware", true);
        CPU_THRESHOLD = c("cpuThresholdPercent", "CPU yükü bu yüzdenin üstündeyse 'yüksek' sayılır.")
                .defineInRange("cpuThresholdPercent", 90, 50, 99);
        B.pop();

        section("sleep", "Pencere arka plandayken uyku modu. Dünya mantığı (tick) durdurulmaz, sadece görsel/gereksiz yük azalır.");
        ENABLE_SLEEP = c("enableSleepMode", "Uyku modu ana şalteri. Kapalıysa aşağıdakilerin hiçbiri çalışmaz.")
                .define("enableSleepMode", true);
        SLEEP_DELAY = c("sleepDelaySeconds",
                "Odak kaybından kaç saniye sonra uykuya geçilsin (Alt+Tab titremelerinde boşuna geçiş olmasın).")
                .defineInRange("sleepDelaySeconds", 2, 0, 30);
        ENABLE_BG_FPS = c("enableBackgroundFpsLimit", "Arka planda FPS sınırı uygula.")
                .define("enableBackgroundFpsLimit", true);
        BG_FPS = c("backgroundFps", "Arka planda FPS sınırı.")
                .defineInRange("backgroundFps", 5, 1, 60);
        ENABLE_DYNAMIC_RENDER = c("enableDynamicRender", "Arka planda render mesafesini düşür, öne gelince geri yükle.")
                .define("enableDynamicRender", true);
        BG_RENDER = c("backgroundRenderDistance", "Arka plandaki render mesafesi (chunk, en az 2).")
                .defineInRange("backgroundRenderDistance", 2, 2, 32);
        REDUCE_SIM_BG = c("reduceSimulationInBackground",
                "Arka planda simulation mesafesini de düşür. DİKKAT: tek oyunculuda uzaktaki makineler/çiftlikler (Create vb.) durur. Önerilmez.")
                .define("reduceSimulationInBackground", false);
        BG_SIM = c("backgroundSimulationDistance", "Arka plandaki simulation mesafesi (Minecraft'ta en az 5).")
                .defineInRange("backgroundSimulationDistance", 5, 5, 32);
        DISTANCE_IN_MP = c("applyDistanceInMultiplayer",
                "Sunucuda da arka planda render mesafesini düşür. Kapalıysa sunucuda chunk'lar tekrar tekrar indirilmez.")
                .define("applyDistanceInMultiplayer", false);
        ENABLE_AUTO_MUTE = c("enableAutoMute", "Arka planda ana sesi kapat, öne gelince eski değere döndür.")
                .define("enableAutoMute", true);
        ENABLE_PARTICLE_CULL = c("enableParticleCulling", "Arka planda parçacık ayarını minimuma çek, öne gelince geri yükle.")
                .define("enableParticleCulling", true);
        ENABLE_ENTITY_CULL = c("enableEntityCulling", "Arka planda canlı entity çizimlerini atla (entity mantığı çalışmaya devam eder).")
                .define("enableEntityCulling", true);
        ENABLE_RAM_CLEANER = c("enableRamCleaner",
                "Uykuya geçtikten ~5 sn sonra, hâlâ arka plandaysa BİR kez GC iste. JVM bunu garanti etmez ve işletim sistemi RAM'ini anında düşürmez.")
                .define("enableRamCleaner", true);
        B.pop();

        section("culling", "Oyun odaktayken entity çizim kırpma (varsayılan kapalı).");
        FG_CULL_DISTANCE = c("foregroundEntityCullDistance",
                "Bu mesafeden (blok) uzaktaki canlı entity'ler çizilmez. 0 = kapalı.")
                .defineInRange("foregroundEntityCullDistance", 0, 0, 256);
        CULL_ONLY_WHEN_ACTIVE = c("cullOnlyWhenGuardActive",
                "Kırpma yalnızca Guard mesafeleri kısmışken (FPS düşükken) çalışsın.")
                .define("cullOnlyWhenGuardActive", true);
        B.pop();

        section("hud", "Ekrandaki gösterge.");
        SHOW_HUD = c("showHud", "Göstergeyi göster (F8).").define("showHud", true);
        HUD_CORNER = c("hudCorner", "Gösterge köşesi.").defineEnum("hudCorner", Corner.TOP_LEFT);
        HUD_SCALE = c("hudScale", "Gösterge boyutu.").defineInRange("hudScale", 1.0, 0.5, 2.0);
        HUD_FPS = c("hudShowFps", "FPS satırı.").define("hudShowFps", true);
        HUD_RAM = c("hudShowRam", "RAM satırı.").define("hudShowRam", true);
        HUD_CPU = c("hudShowCpu", "CPU satırı.").define("hudShowCpu", true);
        HUD_GUARD = c("hudShowGuard", "Guard seviyesi ve mesafe satırı.").define("hudShowGuard", true);
        B.pop();

        SPEC = B.build();
    }

    private FpsGuardConfig() {}

    // ---- Profile göre etkin değerler

    public static int minRender() {
        return switch (PROFILE.get()) {
            case LIGHT -> Math.max(MIN_RENDER.get(), 8);
            case AGGRESSIVE -> Math.min(MIN_RENDER.get(), 4);
            case EXTREME -> 2;
            default -> MIN_RENDER.get();
        };
    }

    public static int minSim() {
        return switch (PROFILE.get()) {
            case LIGHT -> Math.max(MIN_SIM.get(), 8);
            default -> MIN_SIM.get();
        };
    }

    public static double minEntity() {
        return switch (PROFILE.get()) {
            case LIGHT -> Math.max(MIN_ENTITY.get(), 1.0);
            case AGGRESSIVE, EXTREME -> 0.5;
            default -> MIN_ENTITY.get();
        };
    }

    public static int ramThreshold() {
        return switch (PROFILE.get()) {
            case LIGHT -> Math.max(RAM_THRESHOLD.get(), 92);
            case AGGRESSIVE -> Math.min(RAM_THRESHOLD.get(), 80);
            case EXTREME -> Math.min(RAM_THRESHOLD.get(), 75);
            default -> RAM_THRESHOLD.get();
        };
    }

    public static int emergencyPercent() {
        return switch (PROFILE.get()) {
            case LIGHT -> Math.min(EMERGENCY_PERCENT.get(), 35);
            case EXTREME -> Math.max(EMERGENCY_PERCENT.get(), 70);
            default -> EMERGENCY_PERCENT.get();
        };
    }

    public static long reduceCooldownMs() {
        return 1000L * switch (PROFILE.get()) {
            case LIGHT -> Math.max(REDUCE_COOLDOWN.get(), 8);
            case AGGRESSIVE -> Math.min(REDUCE_COOLDOWN.get(), 2);
            case EXTREME -> 1;
            default -> REDUCE_COOLDOWN.get();
        };
    }

    public static long restoreCooldownMs() {
        return 1000L * switch (PROFILE.get()) {
            case LIGHT -> Math.min(RESTORE_COOLDOWN.get(), 10);
            case AGGRESSIVE -> Math.max(RESTORE_COOLDOWN.get(), 30);
            case EXTREME -> Math.max(RESTORE_COOLDOWN.get(), 45);
            default -> RESTORE_COOLDOWN.get();
        };
    }
}
