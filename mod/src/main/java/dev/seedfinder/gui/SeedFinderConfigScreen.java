package dev.seedfinder.gui;

import dev.seedfinder.config.SeedFinderConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Vanilla settings screen — no external deps needed. */
public class SeedFinderConfigScreen extends Screen {
    private final Screen parent;

    public SeedFinderConfigScreen(Screen parent) {
        super(Text.literal("SeedFinder Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height / 2 - 60;

        // Radius slider
        int current = SeedFinderConfig.getSearchRadiusChunks();
        var slider = new SliderWidget(this.width / 2 - 100, y, 200, 20,
            Text.literal("Radius: " + current + " chunks"), (double) current / 10000) {
            @Override
            protected void updateMessage() {
                int val = (int) (this.value * 10000);
                this.setMessage(Text.literal("Radius: " + val + " chunks"));
            }
            @Override
            protected void applyValue() {
                SeedFinderConfig.setSearchRadius((int) (this.value * 10000));
            }
        };
        addDrawableChild(slider);

        // Radius description
        y += 24;
        addDrawableChild(ButtonWidget.builder(
            Text.literal("100-chunk increments").formatted(Formatting.GRAY),
            b -> {}).dimensions(this.width / 2 - 100, y, 200, 20).build());

        // Back button
        y += 30;
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"), b -> {
                SeedFinderConfig.save();
                client.setScreen(parent);
            }).dimensions(this.width / 2 - 100, y, 200, 20).build());
    }

    @Override
    public void close() {
        SeedFinderConfig.save();
        client.setScreen(parent);
    }
}
