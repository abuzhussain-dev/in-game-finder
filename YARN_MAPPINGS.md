# Yarn 1.21.11+build.6 Mapping Reference

## Class Mappings (obfuscated → deobfuscated)

| Obfuscated | Deobfuscated | Package |
|---|---|---|
| class_437 | `Screen` | `net.minecraft.client.gui.screen` |
| class_342 | `TextFieldWidget` | `net.minecraft.client.gui.widget` |
| class_4185 | `ButtonWidget` | `net.minecraft.client.gui.widget` |
| class_332 | `DrawContext` | `net.minecraft.client.gui` |
| class_310 | `MinecraftClient` | `net.minecraft.client` |
| class_327 | `TextRenderer` | `net.minecraft.client.font` |
| class_2561 | `Text` | `net.minecraft.text` |
| class_124 | `Formatting` | `net.minecraft.util` |
| class_2338 | `BlockPos` | `net.minecraft.util.math` |
| class_1923 | `ChunkPos` | `net.minecraft.util.math` |
| class_243 | `Vec3d` | `net.minecraft.util.math` |
| class_4184 | `Camera` | `net.minecraft.client.render` |
| class_4587 | `MatrixStack` | `net.minecraft.client.util.math` |
| class_287 | `BufferBuilder` | `net.minecraft.client.render` |
| class_9799 | `BufferAllocator` | `net.minecraft.client.util` |
| class_9801 | `BuiltBuffer` | `net.minecraft.client.render` |
| class_11285 | `MappableRingBuffer` | `net.minecraft.client.gl` |
| class_10799 | `RenderPipeline` | `com.mojang.blaze3d.pipeline` |
| class_9779 | `RenderTickCounter` | `net.minecraft.client.render` |
| class_2960 | `Identifier` | `net.minecraft.util` |

## Method Mappings

### Screen
- `method_25426()` → `init()`
- `method_25394()` → `render(DrawContext, int, int, float)`
- `method_25412()` → `tick()`
- `method_25419()` → `close()`
- `method_37063()` → `addDrawableChild(Element)`
- `method_37066()` → `remove(Element)`
- `method_25396()` → `children()`
- `field_22789` → `width`
- `field_22790` → `height`
- `field_22793` → `textRenderer`

### ButtonWidget
- `method_46430(Text, PressAction)` → `builder(Text, PressAction)`
- `method_46434(int, int, int, int)` → `dimensions(x, y, w, h)`
- `method_46431()` → `build()`

### TextFieldWidget
- `method_5465(String)` → `setText(String)`
- `method_5464()` → `getText()`
- `method_1863(Consumer<String>)` → `setChangedListener(Consumer<String>)`

### MinecraftClient
- `method_1551()` → `getInstance()`
- `method_1521()` → `getWindow()`
- `field_1724` → `player`
- `field_1687` → `world`
- `field_1772` → `textRenderer`

### BlockPos
- `method_10263()` → `getX()`
- `method_10261()` → `getY()`
- `method_10260()` → `getZ()`
- `method_10262(Vec3i)` → `getSquaredDistance(Vec3i)`

### Text
- `method_43471(String)` → `Text.translatable(String)`
- `method_43470(String)` → `Text.literal(String)`
- `method_43469(String, Object...)` → `Text.translatable(String, Object...)`

## DrawContext
- `method_25294(x1, y1, x2, y2, color)` → `fill(x1, y1, x2, y2, color)`
- `method_51433(TextRenderer, String, x, y, color, shadow)` → `drawText(TextRenderer, String, x, y, color, boolean)`
