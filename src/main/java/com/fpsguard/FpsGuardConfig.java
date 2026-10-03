package com.fpsguard;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FpsGuardConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("FPS Guard açık mı? (oyun içinde F9 ile de değiştirilir)")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue TARGET_FPS = B
            .comment("Hedef FPS. Ortalama FPS bunun altına inerse mesafeler kısılır.")
            .defineInRange("targetFps", 60, 20, 240);

    public static final ModConfigSpec.IntValue MIN_RENDER = B
            .comment("Guard'ın düşebileceği en düşük render mesafesi (chunk).")
            .defineInRange("minRenderDistance", 6, 2, 32);

    public static final ModConfigSpec.IntValue MIN_SIM = B
            .comment("Guard'ın düşebileceği en düşük simulation mesafesi (tek oyunculu).")
            .defineInRange("minSimulationDistance", 5, 5, 32);

    public static final ModConfigSpec.DoubleValue MIN_ENTITY = B
            .comment("Guard'ın düşebileceği en düşük entity mesafe çarpanı (0.5 = %50).")
            .defineInRange("minEntityDistanceScale", 0.5, 0.5, 5.0);

    public static final ModConfigSpec.IntValue RESTORE_HEADROOM = B
            .comment("FPS, hedefin bu kadar üstüne çıkarsa kısılan mesafeler 1 kademe geri açılır.")
            .defineInRange("restoreHeadroomFps", 30, 10, 200);

    public static final ModConfigSpec.IntValue GRACE_SECONDS = B
            .comment("Dünyaya girdikten sonra bu kadar saniye ayar değiştirme (chunk yüklemesi sırasında FPS zaten düşük olur).")
            .defineInRange("joinGraceSeconds", 20, 0, 120);

    public static final ModConfigSpec.IntValue RAM_THRESHOLD = B
            .comment("Java heap kullanımı bu yüzdeyi 3 saniye aşarsa RAM önlemleri devreye girer.")
            .defineInRange("ramThresholdPercent", 88, 60, 98);

    public static final ModConfigSpec.BooleanValue RAM_CLEANUP = B
            .comment("RAM dolunca parçacıkları temizle ve güvenli anda çöp toplama (GC) iste (en fazla dakikada 1).")
            .define("ramCleanup", true);

    public static final ModConfigSpec.BooleanValue BACKGROUND_THROTTLE = B
            .comment("Oyun penceresi arka plandayken FPS sınırını düşür (CPU/GPU/ısı tasarrufu).")
            .define("backgroundThrottle", true);

    public static final ModConfigSpec.IntValue BACKGROUND_FPS = B
            .comment("Arka plandayken FPS sınırı (10'un katı).")
            .defineInRange("backgroundFpsLimit", 10, 10, 60);

    public static final ModConfigSpec.BooleanValue SHOW_HUD = B
            .comment("Sol üstte küçük FPS/RAM göstergesi (F8 ile açılıp kapanır).")
            .define("showHud", true);

    public static final ModConfigSpec SPEC = B.build();

    private FpsGuardConfig() {}
}
