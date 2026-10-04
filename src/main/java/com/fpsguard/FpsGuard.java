package com.fpsguard;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * FPS Guard - sadece istemci tarafı performans yardımcısı.
 * Doku, shader ya da başka modun grafiğine dokunmaz; oyun ayarlarını (mesafeler, ses, parçacık)
 * gerektiğinde geçici değiştirir ve her zaman eski değerlerine döndürür.
 */
@Mod(value = FpsGuard.MOD_ID, dist = Dist.CLIENT)
public class FpsGuard {
    public static final String MOD_ID = "fpsguard";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FpsGuard(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, FpsGuardConfig.SPEC);
        // Mods menüsünde "Config" düğmesi: tüm ayarlar oyun içinden değiştirilir
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(GuardController::registerKeys);

        SleepMode sleep = new SleepMode();
        GuardController controller = new GuardController(sleep);
        NeoForge.EVENT_BUS.register(controller);
        NeoForge.EVENT_BUS.register(new RenderCulling(sleep, controller));
    }
}
