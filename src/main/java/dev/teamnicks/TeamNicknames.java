package dev.teamnicks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TeamNicknames implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("teamnicknames");

    @Override
    public void onInitializeClient() {
        NickConfig.load();
        if (FabricLoader.getInstance().isModLoaded("fabric-api")
                || FabricLoader.getInstance().isModLoaded("fabric-command-api-v2")) {
            NickCommands.register();
        } else {
            LOGGER.info("[TeamNicknames] Fabric API not found: /nicks command disabled. Edit {} instead (reloads automatically).",
                    NickConfig.file());
        }
    }
}
