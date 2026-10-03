package com.fpsguard;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * FPS Guard - sadece istemci tarafı FPS / RAM / CPU koruyucu.
 * Doku, shader ya da başka modun grafiğine dokunmaz; sadece oyun ayarlarını
 * (render / simulation / entity mesafesi) gerektiğinde kademeli kısar ve düzelince geri açar.
 */
@Mod(value = FpsGuard.MOD_ID, dist = Dist.CLIENT)
public class FpsGuard {
    public static final String MOD_ID = "fpsguard";

    public FpsGuard(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, FpsGuardConfig.SPEC);
        // Mods menüsünde "Config" düğmesi: tüm ayarlar oyun içinden değiştirilir
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(GuardController::registerKeys);
        NeoForge.EVENT_BUS.register(new GuardController());
    }
}
