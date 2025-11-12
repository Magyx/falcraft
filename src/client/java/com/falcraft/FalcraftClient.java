package com.falcraft;

import com.falcraft.commands.GenerateCommand;
import com.falcraft.commands.RemixCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FalcraftClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("FalcraftClient");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Falcraft client...");
        
        // Register the client-side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            RemixCommand.register(dispatcher);
            LOGGER.info("Registered /fal remix command");
            
            GenerateCommand.register(dispatcher);
            LOGGER.info("Registered /fal generate command");
        });
        
        LOGGER.info("Falcraft client initialized!");
    }
}

