package dev.seedfinder.gui;

import dev.seedfinder.config.SeedFinderConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class SeedFinderConfigScreen {

    public static Screen create(Screen parent) {
        var builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("SeedFinder Settings"))
            .setSavingRunnable(SeedFinderConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));
        ConfigEntryBuilder eb = builder.entryBuilder();

        general.addEntry(eb.startIntSlider(
                Text.literal("Search Radius (chunks)"),
                SeedFinderConfig.getSearchRadiusChunks(), 100, 10000)
            .setDefaultValue(640)
            .setTextGetter(v -> Text.literal("Search Radius: " + v + " chunks"))
            .setSaveConsumer(SeedFinderConfig::setSearchRadius)
            .build());

        return builder.build();
    }

    private SeedFinderConfigScreen() {}
}
