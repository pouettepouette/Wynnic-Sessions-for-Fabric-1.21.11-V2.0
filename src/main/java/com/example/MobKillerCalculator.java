






package com.example;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class MobKillerCalculator implements ModInitializer {
    public static double calculateProbability(double lootBonus, double lootQuality, double charmBonus) {
        return (1.0 / 142857.0) * (1.0 + (lootBonus + lootQuality) / 100.0) * (1.0 + charmBonus / 100.0);
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("calcprob")
                    .then(Commands.argument("lootBonus", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("lootQuality", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("charmBonus", DoubleArgumentType.doubleArg())
                                .executes(context -> {
                                    double lootBonus = DoubleArgumentType.getDouble(context, "lootBonus");
                                    double lootQuality = DoubleArgumentType.getDouble(context, "lootQuality");
                                    double charmBonus = DoubleArgumentType.getDouble(context, "charmBonus");
                                    double probability = calculateProbability(lootBonus, lootQuality, charmBonus);

                                    context.getSource().sendSuccess(() ->
                                        Component.literal(String.format("Drop chance per mob: %.8f", probability)),
                                        false
                                    );
                                    return 1;
                                })
                            )
                        )
                    )
            );
        });
    }
}
