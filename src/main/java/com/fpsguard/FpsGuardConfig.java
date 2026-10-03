package com.fpsguard;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tüm ayarlar. Oyun içinde: Mods -> FPS Guard -> Config. Değişiklikler anında uygulanır. */
public final class FpsGuardConfig {
    public enum Profile { CUSTOM, LIGHT, BALANCED, AGGRESSIVE }

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    private static ModConfigSpec.Builder c(String name, String comment) {
        return B.comment(comment).translation("fpsguard.configuration." + name);
    }

    // ---- Genel
    public static final ModConfigSpec.BooleanValue ENABLED = c("enabled",
            "FPS Guard açık mı? (oyun içinde F9)").define("enabled", true);
    public static final ModConfigSpec.EnumValue<Profile> PROFILE = c("profile",
            "Profil (oyun içinde F7 ile değişir). LIGHT=az müdahale, BALANCED=dengeli, AGGRESSIVE=sert, CUSTOM=sadece aşağıdaki değerler.")
            .defineEnum("profile", Profile.BALANCED);
    public static final ModConfigSpec.IntValue TARGET_FPS = c("targetFps",
            "Hedef FPS. Ortalama FPS bunun altına inerse mesafeler kısılır.")
            .defineInRange("targetFps", 60, 20, 240);
    public static final ModConfigSpec.IntValue SAMPLE_SECONDS = c("sampleSeconds",
            "FPS ortalaması kaç saniyeye bakılarak hesaplansın.")
            .defineInRange("sampleSeconds", 4, 2, 10);
    public static final ModConfigSpec.IntValue GRACE_SECONDS = c("joinGraceSeconds",
            "Dünyaya girince bu kadar saniye ayar değiştirme (chunk yüklerken FPS zaten düşük olur).")
            .defineInRange("joinGraceSeconds", 20, 0, 120);

    // ---- Mesafe kısma
    public static final ModConfigSpec.BooleanValue ADJUST_RENDER = c("adjustRender",
            "Render mesafesini kıs.").define("adjustRender", true);
    public static final ModConfigSpec.BooleanValue ADJUST_SIM = c("adjustSimulation",
            "Simulation mesafesini kıs (tek oyunculu).").define("adjustSimulation", true);
    public static final ModConfigSpec.BooleanValue ADJUST_ENTITY = c("adjustEntity",
            "Entity çizim mesafesini kıs.").define("adjustEntity", true);
    public static final ModConfigSpec.IntValue MIN_RENDER = c("minRenderDistance",
            "En düşük render mesafesi (chunk).").defineInRange("minRenderDistance", 6, 2, 32);
    public static final ModConfigSpec.IntValue MIN_SIM = c("minSimulationDistance",
            "En düşük simulation mesafesi.").defineInRange("minSimulationDistance", 5, 5, 32);
    public static final ModConfigSpec.DoubleValue MIN_ENTITY = c("minEntityDistanceScale",
            "En düşük entity mesafe çarpanı (0.5 = %50).").defineInRange("minEntityDistanceScale", 0.5, 0.5, 5.0);
    public static final ModConfigSpec.IntValue REDUCE_COOLDOWN = c("reduceCooldownSeconds",
            "İki kısma arasındaki en az süre (sn).").defineInRange("reduceCooldownSeconds", 4, 1, 30);
    public static final ModConfigSpec.IntValue RESTORE_COOLDOWN = c("restoreCooldownSeconds",
            "İki geri açma arasındaki en az süre (sn).").defineInRange("restoreCooldownSeconds", 20, 5, 120);
    public static final ModConfigSpec.IntValue RESTORE_HEADROOM = c("restoreHeadroomFps",
            "FPS hedefin bu kadar üstüne çıkarsa kısılan mesafeler 1 kademe geri açılır.")
            .defineInRange("restoreHeadroomFps", 30, 10, 200);

    // ---- RAM
    public static final ModConfigSpec.IntValue RAM_THRESHOLD = c("ramThresholdPercent",
            "Java heap kullanımı bu yüzdeyi 3 sn aşarsa RAM önlemleri devreye girer.")
            .defineInRange("ramThresholdPercent", 88, 60, 98);
    public static final ModConfigSpec.BooleanValue RAM_CLEANUP = c("ramCleanup",
            "RAM dolunca çöp toplama (GC) iste.").define("ramCleanup", true);
    public static final ModConfigSpec.IntValue GC_COOLDOWN = c("gcCooldownSeconds",
            "İki GC isteği arasındaki en az süre (sn).").defineInRange("gcCooldownSeconds", 60, 20, 600);

    // ---- CPU
    public static final ModConfigSpec.BooleanValue CPU_AWARE = c("cpuAware",
            "Oyunun CPU yükü yüksekse ve FPS düşükse mesafeyi 2 kademe birden kıs, CPU yüksekken geri açma.")
            .define("cpuAware", true);
    public static final ModConfigSpec.IntValue CPU_THRESHOLD = c("cpuThresholdPercent",
            "CPU yükü bu yüzdenin üstündeyse 'yüksek' sayılır.").defineInRange("cpuThresholdPercent", 90, 50, 99);

    // ---- Arka plan
    public static final ModConfigSpec.BooleanValue BACKGROUND_THROTTLE = c("backgroundThrottle",
            "Pencere arka plandayken FPS sınırını düşür.").define("backgroundThrottle", true);
    public static final ModConfigSpec.IntValue BACKGROUND_FPS = c("backgroundFpsLimit",
            "Arka plandayken FPS sınırı (10'un katı).").defineInRange("backgroundFpsLimit", 10, 10, 60);

    // ---- Gösterge
    public static final ModConfigSpec.BooleanValue SHOW_HUD = c("showHud",
            "Göstergeyi göster (F8).").define("showHud", true);
    public static final ModConfigSpec.EnumValue<Corner> HUD_CORNER = c("hudCorner",
            "Gösterge köşesi.").defineEnum("hudCorner", Corner.TOP_LEFT);
    public static final ModConfigSpec.DoubleValue HUD_SCALE = c("hudScale",
            "Gösterge boyutu.").defineInRange("hudScale", 1.0, 0.5, 2.0);
    public static final ModConfigSpec.BooleanValue HUD_FPS = c("hudShowFps",
            "FPS satırı.").define("hudShowFps", true);
    public static final ModConfigSpec.BooleanValue HUD_RAM = c("hudShowRam",
            "RAM satırı.").define("hudShowRam", true);
    public static final ModConfigSpec.BooleanValue HUD_CPU = c("hudShowCpu",
            "CPU satırı.").define("hudShowCpu", true);
    public static final ModConfigSpec.BooleanValue HUD_GUARD = c("hudShowGuard",
            "Guard seviyesi ve mesafe satırı.").define("hudShowGuard", true);

    public static final ModConfigSpec SPEC = B.build();

    private FpsGuardConfig() {}

    // ---- Profile göre etkin değerler

    public static int minRender() {
        return switch (PROFILE.get()) {
            case LIGHT -> Math.max(MIN_RENDER.get(), 8);
            case AGGRESSIVE -> Math.min(MIN_RENDER.get(), 4);
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
            case AGGRESSIVE -> 0.5;
            default -> MIN_ENTITY.get();
        };
    }

    public static int ramThreshold() {
        return switch (PROFILE.get()) {
            case LIGHT -> Math.max(RAM_THRESHOLD.get(), 92);
            case AGGRESSIVE -> Math.min(RAM_THRESHOLD.get(), 80);
            default -> RAM_THRESHOLD.get();
        };
    }

    public static long reduceCooldownMs() {
        return 1000L * switch (PROFILE.get()) {
            case LIGHT -> Math.max(REDUCE_COOLDOWN.get(), 8);
            case AGGRESSIVE -> Math.min(REDUCE_COOLDOWN.get(), 2);
            default -> REDUCE_COOLDOWN.get();
        };
    }

    public static long restoreCooldownMs() {
        return 1000L * switch (PROFILE.get()) {
            case LIGHT -> Math.min(RESTORE_COOLDOWN.get(), 10);
            case AGGRESSIVE -> Math.max(RESTORE_COOLDOWN.get(), 30);
            default -> RESTORE_COOLDOWN.get();
        };
    }
}
