package immersive_paintings.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import immersive_paintings.resources.Painting;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class PaintingWidget extends ButtonWidget {
    private final Painting painting;
    private final PressAction onPressRight;
    private int button;

    public PaintingWidget(Painting painting, int x, int y, int width, int height, PressAction onPress, PressAction onPressRight, TooltipSupplier tooltipSupplier) {
        super(x, y, width, height, new LiteralText("Painting"), onPress, tooltipSupplier);
        this.onPressRight = onPressRight;
        this.painting = painting;
    }

    @Override
    public void onPress() {
        if (button == 0) {
            onPress.onPress(this);
        } else {
            onPressRight.onPress(this);
        }
        button = 0;
    }

    @Override
    protected boolean isValidClickButton(int button) {
        this.button = button;
        return button == 0 || button == 1;
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, painting.thumbnail.textureIdentifier);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        matrices.push();
        int tw = painting.thumbnail.image == null ? 32 : painting.thumbnail.image.getWidth();
        int th = painting.thumbnail.image == null ? 32 : painting.thumbnail.image.getHeight();
        float scale = Math.min((float)width / tw, (float)height / th);
        if (isHovered()) {
            scale *= 1.1;
        }
        matrices.translate(x + (this.width - tw * scale) / 2, y + (this.height - th * scale) / 2, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        drawTexture(matrices, 0, 0, 0, 0, tw, th, tw, th);
        matrices.pop();

        if (isHovered()) {
            renderTooltip(matrices, mouseX, mouseY);
        }
    }
}

