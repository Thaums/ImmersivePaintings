package immersive_paintings.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import immersive_paintings.Main;
import immersive_paintings.client.gui.widget.IntegerSliderWidget;
import immersive_paintings.client.gui.widget.PaintingWidget;
import immersive_paintings.client.gui.widget.PercentageSliderWidget;
import immersive_paintings.cobalt.network.NetworkHandler;
import immersive_paintings.network.c2s.PaintingModifyRequest;
import immersive_paintings.network.c2s.RegisterPaintingRequest;
import immersive_paintings.resources.ClientPaintingManager;
import immersive_paintings.resources.Paintings;
import immersive_paintings.util.FlowingText;
import immersive_paintings.util.ImageManipulations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.*;

public class ImmersivePaintingScreen extends Screen {
    final int entityId;

    private String filteredString = "";
    private int filteredResolution = 0;
    private int filteredWidth = 0;
    private int filteredHeight = 0;
    private final List<Identifier> filteredPaintings = new ArrayList<>();

    private int selectionPage;
    private Page page;

    private ButtonWidget pageWidget;

    private final List<PaintingWidget> paintingWidgetList = new LinkedList<>();
    private NativeImage currentImage;
    private String currentImageName;
    private PixelatorSettings settings;
    private NativeImage pixelatedImage;

    private List<File> screenshots = List.of();
    private int screenshotPage;

    public ImmersivePaintingScreen(int entityId) {
        super(new LiteralText("Painting"));
        this.entityId = entityId;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        setPage(Page.SELECTION_YOURS);
        clearSearch();
        updateSearch();

        File file = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
        File[] files = file.listFiles(v -> v.getName().endsWith(".png"));
        if (files != null) {
            screenshots = Arrays.stream(files).toList();
        }
    }

    private void clearSearch() {
        filteredString = "";
        filteredResolution = 0;
        filteredWidth = 0;
        filteredHeight = 0;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);

        if (page == Page.NEW) {
            List<Text> wrap = FlowingText.wrap(new TranslatableText("Drop an image here, enter a file path or URL or select a screenshot to start."), 220);
            int y = height / 2 - 40 - wrap.size() * 12;
            for (Text text : wrap) {
                drawCenteredText(matrices, textRenderer, text, width / 2, y, 0xFFFFFFFF);
                y += 12;
            }
        } else if (page == Page.CREATE) {
            int maxWidth = 190;
            int maxHeight = 135;
            int tw = settings.resolution * settings.width;
            int th = settings.resolution * settings.height;
            float size = Math.min((float)maxWidth / tw, (float)maxHeight / th);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, Main.locate("temp_pixelated"));
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();

            matrices.translate(width / 2.0f - tw * size / 2.0f, height / 2.0f - th * size / 2.0f, 0.0f);
            matrices.scale(size, size, 1.0f);
            drawTexture(matrices, 0, 0, 0, 0, tw, th, tw, th);
            matrices.pop();
        }
    }


    private void rebuild() {
        clearChildren();

        // filters
        if (page != Page.CREATE) {
            addDrawableChild(new ButtonWidget(width / 2 - 50 - 150, height / 2 - 90 - 22, 100, 20, new LiteralText("Yours"), sender -> setPage(Page.SELECTION_YOURS))).active = page != Page.SELECTION_YOURS;
            addDrawableChild(new ButtonWidget(width / 2 - 50 - 50, height / 2 - 90 - 22, 100, 20, new LiteralText("Datapacks"), sender -> setPage(Page.SELECTION_DATAPACKS))).active = page != Page.SELECTION_DATAPACKS;
            addDrawableChild(new ButtonWidget(width / 2 - 50 + 50, height / 2 - 90 - 22, 100, 20, new LiteralText("Players"), sender -> setPage(Page.SELECTION_PLAYERS))).active = page != Page.SELECTION_PLAYERS;
            addDrawableChild(new ButtonWidget(width / 2 - 50 + 150, height / 2 - 90 - 22, 100, 20, new LiteralText("New Painting"), sender -> setPage(Page.NEW))).active = page != Page.NEW;
        }

        switch (page) {
            case NEW -> {
                //URL
                TextFieldWidget textFieldWidget = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2 - 90, height / 2 - 40, 180, 20,
                        new LiteralText("URL")));
                textFieldWidget.setMaxLength(1024);

                addDrawableChild(new ButtonWidget(width / 2 - 40, height / 2 - 15, 80, 20, new TranslatableText("Load"), sender -> loadImage(Path.of(textFieldWidget.getText()))));

                //screenshots
                rebuildScreenshots();

                //screenshot page
                addDrawableChild(new ButtonWidget(width / 2 - 65, height / 2 + 70, 30, 20, new LiteralText("<<"), sender -> setScreenshotPage(selectionPage - 1)));
                pageWidget = addDrawableChild(new ButtonWidget(width / 2 - 65 + 30, height / 2 + 70, 70, 20, new LiteralText(""), sender -> {
                }));
                addDrawableChild(new ButtonWidget(width / 2 - 65 + 100, height / 2 + 70, 30, 20, new LiteralText(">>"), sender -> setScreenshotPage(selectionPage + 1)));
                setScreenshotPage(screenshotPage);
            }
            case CREATE -> {
                //Identifier
                TextFieldWidget textFieldWidget = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2 - 90, height / 2 - 100, 180, 20,
                        new LiteralText("Identifier")));
                textFieldWidget.setMaxLength(256);
                textFieldWidget.setText(currentImageName);

                int y = height / 2 - 60;

                //width
                addDrawableChild(new IntegerSliderWidget(width / 2 - 200, y, 100, 20, "Width: %s blocks", 3, 1, 16, v -> {
                    settings.width = v;
                    pixelateImage();
                }));
                y += 22;

                //height
                addDrawableChild(new IntegerSliderWidget(width / 2 - 200, y, 100, 20, "Height: %s blocks", 2, 1, 16, v -> {
                    settings.height = v;
                    pixelateImage();
                }));
                y += 22;

                //resolution
                int[] resolutions = new int[] {8, 16, 32, 64};
                int x = width / 2 - 200;
                List<ButtonWidget> list = new LinkedList<>();
                for (int res : resolutions) {
                    ButtonWidget widget = addDrawableChild(new ButtonWidget(x, y, 25, 20, new LiteralText(String.valueOf(res)), v -> {
                        settings.resolution = res;
                        pixelateImage();
                        list.forEach(b -> b.active = true);
                        v.active = false;
                    }));
                    widget.active = settings.resolution != res;
                    list.add(widget);
                    x += 25;
                }
                y += 22;
                y += 10;

                //color reduction
                addDrawableChild(new IntegerSliderWidget(width / 2 - 200, y, 100, 20, "%s colors", 12, 1, 25, v -> {
                    settings.colors = v;
                    pixelateImage();
                }));
                y += 22;

                //dither
                addDrawableChild(new PercentageSliderWidget(width / 2 - 200, y, 100, 20, "%s%% dither", 0.25, v -> {
                    settings.dither = v;
                    pixelateImage();
                }));

                //offset X
                y = height / 2 - 30;
                addDrawableChild(new PercentageSliderWidget(width / 2 + 100, y, 100, 20, "%s%% X offset", 0.5, v -> {
                    settings.offsetX = v;
                    pixelateImage();
                }));
                y += 22;

                //offset Y
                addDrawableChild(new PercentageSliderWidget(width / 2 + 100, y, 100, 20, "%s%% Y offset", 0.5, v -> {
                    settings.offsetY = v;
                    pixelateImage();
                }));
                y += 22;

                //offset
                addDrawableChild(new PercentageSliderWidget(width / 2 + 100, y, 100, 20, "%s%% zoom", 1, 1, 3, v -> {
                    settings.zoom = v;
                    pixelateImage();
                }));

                addDrawableChild(new ButtonWidget(width / 2 - 85, height / 2 + 70, 80, 20, new LiteralText("Cancel"), v -> setPage(Page.NEW)));

                addDrawableChild(new ButtonWidget(width / 2 + 5, height / 2 + 70, 80, 20, new LiteralText("Save"),
                        v -> {
                            NetworkHandler.sendToServer(new RegisterPaintingRequest(currentImageName, new Paintings.PaintingData(
                                    pixelatedImage,
                                    settings.width,
                                    settings.height,
                                    settings.resolution
                            )));
                            setPage(Page.SELECTION_YOURS);
                        }));
            }
            case SELECTION_YOURS, SELECTION_DATAPACKS, SELECTION_PLAYERS -> {
                rebuildPaintings();

                // page
                addDrawableChild(new ButtonWidget(width / 2 - 65 - 100, height / 2 - 90, 30, 20, new LiteralText("<<"), sender -> setSelectionPage(selectionPage - 1)));
                pageWidget = addDrawableChild(new ButtonWidget(width / 2 - 65 - 70, height / 2 - 90, 70, 20, new LiteralText(""), sender -> {
                }));
                addDrawableChild(new ButtonWidget(width / 2 - 65, height / 2 - 90, 30, 20, new LiteralText(">>"), sender -> setSelectionPage(selectionPage + 1)));
                setSelectionPage(selectionPage);

                //search
                TextFieldWidget textFieldWidget = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2 + 50 - 35, height / 2 - 90, 130, 20,
                        new LiteralText("search")));
                textFieldWidget.setMaxLength(64);
                textFieldWidget.setChangedListener((s) -> {
                    filteredString = s;
                    updateSearch();
                });

                //filter resolution
                int[] resolutions = new int[] {0, 8, 16, 32, 64};
                int x = width / 2 - 200;
                List<ButtonWidget> list = new LinkedList<>();
                for (int res : resolutions) {
                    LiteralText text = new LiteralText(res == 0 ? "all" : String.valueOf(res));
                    ButtonWidget widget = addDrawableChild(new ButtonWidget(x, height / 2 - 50, 25, 20, text, v -> {
                        filteredResolution = res;
                        updateSearch();
                        list.forEach(b -> b.active = true);
                        v.active = false;
                    }));
                    widget.active = res != filteredResolution;
                    list.add(widget);
                    x += 25;
                }

                //width
                TextFieldWidget textField = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2 + 100, height / 2 - 90, 40, 20,
                        new LiteralText("width")));
                textField.setMaxLength(2);
                textField.setChangedListener((s) -> {
                    try {
                        filteredWidth = Integer.parseInt(s);
                        updateSearch();
                    } catch (NumberFormatException ignored) {
                    }
                });

                //height
                textField = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2 + 100 + 50, height / 2 - 90, 40, 20,
                        new LiteralText("height")));
                textField.setMaxLength(2);
                textField.setChangedListener((s) -> {
                    try {
                        filteredHeight = Integer.parseInt(s);
                        updateSearch();
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
        }
    }

    private void rebuildPaintings() {
        for (PaintingWidget w : paintingWidgetList) {
            remove(w);
        }
        paintingWidgetList.clear();

        // paintings
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 8; x++) {
                int i = y * 8 + x + selectionPage * 24;
                if (i >= 0 && i < filteredPaintings.size()) {
                    Identifier identifier = filteredPaintings.get(i);
                    Paintings.PaintingData painting = ClientPaintingManager.getPainting(identifier);
                    paintingWidgetList.add(addDrawableChild(new PaintingWidget(painting, (int)(width / 2 + (x - 3.5) * 52) - 24, height / 2 - 60 + y * 52, 48, 48,
                            sender -> NetworkHandler.sendToServer(new PaintingModifyRequest(entityId, identifier)),
                            (ButtonWidget b, MatrixStack matrices, int mx, int my) -> renderTooltip(matrices, List.of(
                                    new LiteralText(identifier.getPath()),
                                    new LiteralText("author").formatted(Formatting.ITALIC),
                                    new LiteralText(painting.width + "x" + painting.height + " at " + painting.resolution + "px").formatted(Formatting.ITALIC)
                            ), mx, my))));
                } else {
                    break;
                }
            }
        }
    }

    private void rebuildScreenshots() {
        for (PaintingWidget w : paintingWidgetList) {
            remove(w);
        }
        paintingWidgetList.clear();

        // screenshots
        for (int x = 0; x < 6; x++) {
            int i = x + screenshotPage * 6;
            if (i >= 0 && i < screenshots.size()) {
                File file = screenshots.get(i);
                NativeImage image = loadImage(file.toPath(), Main.locate("screenshot_" + x));
                if (image != null) {
                    Paintings.PaintingData painting = new Paintings.PaintingData(image, 16);
                    painting.textureIdentifier = Main.locate("screenshot_" + x);
                    paintingWidgetList.add(addDrawableChild(new PaintingWidget(painting, (int)(width / 2 + (x - 2.5) * 68) - 32, height / 2 + 15, 64, 48,
                            (b) -> {
                                currentImage = image;
                                currentImageName = file.getName();
                                settings = new PixelatorSettings(currentImage);
                                setPage(Page.CREATE);
                                pixelateImage();
                            },
                            (ButtonWidget b, MatrixStack matrices, int mx, int my) -> renderTooltip(matrices, new LiteralText(file.getName()), mx, my))));
                } else {
                    break;
                }
            }
        }
    }

    private void setPage(Page page) {
        this.page = page;
        rebuild();
    }

    private void updateSearch() {
        filteredPaintings.clear();
        filteredPaintings.addAll(ClientPaintingManager.getPaintings().entrySet().stream()
                .filter(v -> v.getKey().toString().contains(filteredString))
                .filter(v -> filteredResolution == 0 || v.getValue().resolution == filteredResolution)
                .filter(v -> filteredWidth == 0 || v.getValue().width == filteredWidth)
                .filter(v -> filteredHeight == 0 || v.getValue().height == filteredHeight)
                .map(Map.Entry::getKey)
                .toList());
        setSelectionPage(selectionPage);
    }

    private void setSelectionPage(int p) {
        selectionPage = Math.min(getMaxPages() - 1, Math.max(0, p));
        rebuildPaintings();
        pageWidget.setMessage(new LiteralText((selectionPage + 1) + " / " + getMaxPages()));
    }

    private int getMaxPages() {
        return (int)Math.ceil(filteredPaintings.size() / 24.0);
    }

    private void setScreenshotPage(int p) {
        screenshotPage = Math.min(getScreenshotMaxPages() - 1, Math.max(0, p));
        rebuildScreenshots();
        pageWidget.setMessage(new LiteralText((screenshotPage + 1) + " / " + getScreenshotMaxPages()));
    }

    private int getScreenshotMaxPages() {
        return (int)Math.ceil(screenshots.size() / 8.0);
    }

    @Override
    public void filesDragged(List<Path> paths) {
        Path path = paths.get(0);
        loadImage(path);
    }


    private void loadImage(Path path) {
        currentImage = loadImage(path, Main.locate("temp"));
        if (currentImage != null) {
            currentImageName = path.getFileName().toString().replaceFirst("[.][^.]+$", "");
            settings = new PixelatorSettings(currentImage);
            setPage(Page.CREATE);
            pixelateImage();
        }
    }

    private NativeImage loadImage(Path path, Identifier identifier) {
        try {
            FileInputStream stream = new FileInputStream(path.toFile());
            NativeImage nativeImage = NativeImage.read(stream);
            MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, new NativeImageBackedTexture(nativeImage));
            stream.close();
            return nativeImage;
        } catch (Exception ignored) {
        }
        return null;
    }

    private void pixelateImage() {
        pixelatedImage = new NativeImage(settings.resolution * settings.width, settings.resolution * settings.height, false);

        //downscale
        ImageManipulations.resize(pixelatedImage, currentImage, settings.zoom, settings.offsetX, settings.offsetY);

        //dither
        if (settings.dither > 0) {
            if (settings.colors > 1) {
                ImageManipulations.dither(pixelatedImage, settings.dither / settings.colors);
            } else {
                ImageManipulations.dither(pixelatedImage, settings.dither / 16.0);
            }
        }

        //reduce colors
        if (settings.colors > 1) {
            ImageManipulations.reduceColors(pixelatedImage, settings.colors);
        }

        MinecraftClient.getInstance().getTextureManager().registerTexture(Main.locate("temp_pixelated"), new NativeImageBackedTexture(pixelatedImage));
    }

    enum Page {
        SELECTION_YOURS,
        SELECTION_DATAPACKS,
        SELECTION_PLAYERS,
        NEW,
        CREATE
    }

    static final class PixelatorSettings {
        public double dither;
        public int colors;
        public int resolution;
        public int width;
        public int height;
        public double offsetX;
        public double offsetY;
        public double zoom;

        PixelatorSettings(double dither, int colors, int resolution, int width, int height, double offsetX, double offsetY, double zoom) {
            this.dither = dither;
            this.colors = colors;
            this.resolution = resolution;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.zoom = zoom;
        }

        PixelatorSettings(NativeImage currentImage) {
            this(0.25, 10, 32, 1, 1, 0.5, 0.5, 1);
            double bestScore = 100.0;
            int w = 1, h = 1;
            double target = currentImage.getWidth() / (double)currentImage.getHeight();
            for (int attempt = 0; attempt < 16; attempt++) {
                double e0 = Math.abs(w / (h + 1.0) - target) * Math.sqrt(w * h);
                double e1 = Math.abs((w + 1.0) / h - target) * Math.sqrt(w * h);
                if (e0 < bestScore && e1 < bestScore) {
                    if (e0 < e1) {
                        h++;
                        this.height = h;
                        bestScore = e0;
                    } else {
                        w++;
                        this.width = w;
                        bestScore = e1;
                    }
                }
            }
        }
    }
}