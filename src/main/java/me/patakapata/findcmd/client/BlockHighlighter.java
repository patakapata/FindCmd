package me.patakapata.findcmd.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.GlAllocationUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import static me.patakapata.findcmd.client.FindCmdClient.LOGGER;
import static org.lwjgl.opengl.GL30.*;

public class BlockHighlighter {
    private static final BlockHighlighter INSTANCE = new BlockHighlighter();
    private static final Random RAND = new Random();

    private final Identifier TEXTURE = new Identifier("findcmd", "textures/misc/overlay.png");
    private int TEXTURE_ID;
    private final Set<Entry> highlightEntries = new HashSet<>();
    private final int bufferRoundSize = 1024;
    private ByteBuffer buffer = GlAllocationUtils.allocateByteBuffer(bufferRoundSize);
    private final FloatBuffer PROJ_MAT_BUFF = GlAllocationUtils.allocateByteBuffer(4 * 4 * 4).asFloatBuffer();
    private final FloatBuffer MV_MAT_BUFF = GlAllocationUtils.allocateByteBuffer(4 * 4 * 4).asFloatBuffer();

    private final int BYTE_SIZE = Byte.SIZE;
    private final int FLOAT_SIZE = Float.SIZE / BYTE_SIZE;

    private boolean bufferSizeChanged = false;
    private boolean entryChanged = false;
    private int vertices = 0;
    private int vbo;
    private int vao;
    private Shader shader;

    public static BlockHighlighter getInstance() {
        return INSTANCE;
    }

    public static int randomColor() {
        return 0xFF000000 | RAND.nextInt(255) << 16 | RAND.nextInt(255) << 8 | RAND.nextInt(255);
    }

    private BlockHighlighter() {
    }

    public void onClientStart(MinecraftClient mc) {
        vbo = glGenBuffers();
        vao = glGenVertexArrays();

        TEXTURE_ID = mc.getTextureManager().getTexture(TEXTURE).getGlId();

        if (vbo == 0) {
            LOGGER.error("Failed to generate vertex buffer object");
        } else {
            LOGGER.info("Generate vertex buffer object successful with id " + vbo);
        }

        if (vao == 0) {
            LOGGER.error("Failed to generate vertex array object");
        } else {
            LOGGER.info("Generate vertex array object successful with id " + vao);
        }

        setupBuffer();
    }

    private void setupBuffer() {
        shader = GameRenderer.getPositionColorTexShader();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 0L, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        int program = shader.getProgramRef();
        glBindVertexArray(vao);

        int posLoc = glGetAttribLocation(program, "Position");
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 36, 0L);
        glEnableVertexAttribArray(posLoc);

        int colLoc = glGetAttribLocation(program, "Color");
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(colLoc, 4, GL_FLOAT, false, 36, 12L);
        glEnableVertexAttribArray(colLoc);

        int texLoc = glGetAttribLocation(program, "UV0");
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(texLoc, 2, GL_FLOAT, false, 36, 28L);
        glEnableVertexAttribArray(texLoc);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        checkGlError("setup - unbind buffers");

        LOGGER.info("Complete buffers setup");
    }

    public void onRenderWorld(WorldRenderContext ctx) {
        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        MatrixStack matrices = ctx.matrixStack();

        matrices.push();
        matrices.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        matrices.pop();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1, -1);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.getModelViewMatrix().load(matrix);

        shader = GameRenderer.getPositionColorTexShader();
        if (shader == null) {
            // コアシェーダーが何らかの理由で存在しない場合描画せずに終了
            return;
        }
        int program = shader.getProgramRef();
        glUseProgram(program);

        // -------------------------------------------------- //
        // 投影行列
        int projMatLoc = glGetUniformLocation(program, "ProjMat");
        PROJ_MAT_BUFF.position(0);
        Matrix4f projMat = ctx.projectionMatrix();
        projMat.writeColumnMajor(PROJ_MAT_BUFF);
        PROJ_MAT_BUFF.position(0);
        glUniformMatrix4fv(projMatLoc, false, PROJ_MAT_BUFF);

        // -------------------------------------------------- //
        // モデル・ビュー行列
        int mvMatLoc = glGetUniformLocation(program, "ModelViewMat");
        MV_MAT_BUFF.position(0);
        matrix.writeColumnMajor(MV_MAT_BUFF);
        MV_MAT_BUFF.position(0);
        glUniformMatrix4fv(mvMatLoc, false, MV_MAT_BUFF);

        // -------------------------------------------------- //
        // カラーモジュレーター
        int colModLoc = glGetUniformLocation(program, "ColorModulator");
        glUniform4f(colModLoc, 1f, 1f, 1f, 1f);

        // -------------------------------------------------- //
        // テクスチャのバインド
        glActiveTexture(GL_TEXTURE0);
        RenderSystem.disableTexture();
        RenderSystem.bindTexture(TEXTURE_ID);
        glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);

        if (entryChanged) {
            // 再構成
            entryChanged = false;
            LOGGER.debug("バッファー再構成中…");
            buffer.clear();
            int i = 0;
            int size = highlightEntries.size();
            for (Entry entry : highlightEntries) {
                // 最初の要素以外は縮退を最初に付ける
                buildBox(entry.getRegion(), entry.getColor(), 0, 0, i != 0, true);
                buildBox(entry.getRegion().expand(0.01), 0xFF000000, 0, 0.5F, true, true);
                // 最後の要素以外は縮退を最後に付ける
                buildBox(entry.getRegion().expand(0.02), entry.getColor(), 0, 0.5F, true, ++i != size);
            }
            buffer.flip();
            vertices = buffer.limit() / 36;
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            if (bufferSizeChanged) {
                bufferSizeChanged = false;
                glBufferData(GL_ARRAY_BUFFER, buffer.capacity(), GL_DYNAMIC_DRAW);
            }
            glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            LOGGER.debug("バッファー再構成完了");
        }

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, vertices);
        glBindVertexArray(0);

        RenderSystem.enableDepthTest();
        RenderSystem.disablePolygonOffset();
        RenderSystem.disableBlend();
        RenderSystem.getModelViewMatrix().loadIdentity();

        checkGlError("render tail");
    }

    public void onClientTick(MinecraftClient ignored) {
        Iterator<Entry> itr = highlightEntries.iterator();
        Entry entry;
        while (itr.hasNext()) {
            entry = itr.next();
            entry.tick();
            if (entry.isExpired()) {
                itr.remove();
                entryChanged = true;
            }
        }
    }

    private void grow(int expectRemainSize) {
        int remain = buffer.capacity() - buffer.position();

        if (remain < expectRemainSize) {
            // 残余量(容量-現在位置)が期待残容量より小さい場合の処理
            int added = buffer.capacity() + expectRemainSize;
            int div = added / bufferRoundSize;
            if (added % bufferRoundSize > 0) div++;

            int newSize = div * bufferRoundSize;
            buffer.flip();
            ByteBuffer oldBuffer = buffer;
            buffer = GlAllocationUtils.allocateByteBuffer(newSize);
            buffer.put(oldBuffer);
            bufferSizeChanged = true;
            FindCmdClient.LOGGER.debug("Grow up Highlighter buffer size to " + newSize + " from " + oldBuffer.capacity());
        }
    }

    private BlockHighlighter vertex(double x, double y, double z) {
        grow(FLOAT_SIZE * 3);
        buffer.putFloat((float) x);
        buffer.putFloat((float) y);
        buffer.putFloat((float) z);

        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    private BlockHighlighter texture(double u, double v) {
        grow(FLOAT_SIZE * 2);
        buffer.putFloat((float) u);
        buffer.putFloat((float) v);

        return this;
    }

    private BlockHighlighter color(double red, double green, double blue, double alpha) {
        grow(FLOAT_SIZE * 4);
        buffer.putFloat((float) red);
        buffer.putFloat((float) green);
        buffer.putFloat((float) blue);
        buffer.putFloat((float) alpha);

        return this;
    }

    public void onClientStop(MinecraftClient ignored) {
        RenderSystem.glDeleteBuffers(vbo);
    }

    private void checkGlError(String location) {
        int error = GL20.glGetError();
        if (error != 0) {
            FindCmdClient.LOGGER.error("Error " + error + " in " + location);
        }
    }

    public void addHighlight(Box box, int color, int lifeTime) {
        if (!alreadyHighlighted(box)) {
            highlightEntries.add(new RegionEntry(box, color, lifeTime));
            entryChanged = true;
        }
    }

    @SuppressWarnings("unused")
    public void addHighlight(BlockPos pos, int color, int lifeTime) {
        addHighlight(new Box(pos), color, lifeTime);
    }

    public void removeHighlight(Box box) {
        if (alreadyHighlighted(box)) {
            highlightEntries.removeIf(entry -> entry.getRegion().equals(box));
            entryChanged = true;
        }
    }

    @SuppressWarnings("unused")
    public void removeHighlight(BlockPos pos) {
        removeHighlight(new Box(pos));
    }

    public boolean alreadyHighlighted(Box box) {
        for (Entry entry : highlightEntries) {
            if (entry.getRegion().equals(box)) return true;
        }

        return false;
    }

    @SuppressWarnings("unused")
    public boolean alreadyHighlighted(BlockPos pos) {
        Box box = new Box(pos);
        for (Entry entry : highlightEntries) {
            if (entry.getRegion().equals(box)) return true;
        }

        return false;
    }

    @SuppressWarnings("SameParameterValue")
    private void buildBox(Box box, int color, float offsetU, float offsetV, boolean useDegenerateHead, boolean useDegenerateTail) {
        float alpha = ColorHelper.Argb.getAlpha(color) / 255F;
        float red = ColorHelper.Argb.getRed(color) / 255F;
        float green = ColorHelper.Argb.getGreen(color) / 255F;
        float blue = ColorHelper.Argb.getBlue(color) / 255F;

        // 下 (Y-)
        if (useDegenerateHead) {
            vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        }
        vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        vertex(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV);
        vertex(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV);
        vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        // 縮退
        vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        // 上 (U+)
        vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        vertex(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV);
        vertex(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV);
        vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        // 縮退
        vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        // 西 (Y-)
        vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        vertex(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV);
        vertex(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV);
        vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        // 縮退
        vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        // 東 (Y+)
        vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        vertex(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV);
        vertex(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV);
        vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        // 縮退
        vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        vertex(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        // 北 (Z-)
        vertex(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV);
        vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV);
        vertex(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        // 縮退
        vertex(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        vertex(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        // 南 (Z+)
        vertex(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV);
        vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV);
        vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV);
        vertex(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        if (useDegenerateTail) {
            vertex(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV);
        }
    }

    @SuppressWarnings("unused")
    private void buildBox(VertexConsumer consumer, BlockPos pos, int color, float offsetU, float offsetV) {
        buildBox(consumer, new Box(pos), color, offsetU, offsetV);
    }

    /**
     * 渡された{@link VertexConsumer}に対して{@link VertexFormats#POSITION_COLOR_TEXTURE}のフォーマットで立方体を作成する
     *
     * @param consumer 立方体のデータを入れる頂点コンシューマー
     * @param box      立方体の場所と大きさ
     * @param color    立方体の色 0xAARRGGBB
     */
    private void buildBox(VertexConsumer consumer, Box box, int color, float offsetU, float offsetV) {
        float alpha = ColorHelper.Argb.getAlpha(color) / 255F;
        float red = ColorHelper.Argb.getRed(color) / 255F;
        float green = ColorHelper.Argb.getGreen(color) / 255F;
        float blue = ColorHelper.Argb.getBlue(color) / 255F;

        // 下 (Y-)
        consumer.vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV).next();
        consumer.vertex(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV).next();
        // 上 (U+)
        consumer.vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV).next();
        consumer.vertex(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV).next();
        // 西 (Y-)
        consumer.vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV).next();
        consumer.vertex(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV).next();
        // 東 (Y+)
        consumer.vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV).next();
        consumer.vertex(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV).next();
        // 北 (Z-)
        consumer.vertex(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV).next();
        consumer.vertex(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV).next();
        // 南 (Z+)
        consumer.vertex(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.0f + offsetV).next();
        consumer.vertex(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(0 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.5f + offsetV).next();
        consumer.vertex(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).texture(1 + offsetU, 0.0f + offsetV).next();
    }

    private interface Entry {
        Box getRegion();

        int getColor();

        boolean isExpired();

        void tick();
    }

    private static class RegionEntry implements Entry {
        private final Box box;
        private final int color;
        private int lifeTime;

        public RegionEntry(Box box, int color, int lifeTime) {
            this.box = box;
            this.color = color;
            this.lifeTime = lifeTime;
        }

        @Override
        public Box getRegion() {
            return this.box;
        }

        @Override
        public int getColor() {
            return this.color;
        }

        @Override
        public boolean isExpired() {
            return this.lifeTime <= 0;
        }

        @Override
        public void tick() {
            if (this.lifeTime >= 1) this.lifeTime--;
        }

        @Override
        public int hashCode() {
            return this.box.hashCode();
        }
    }
}
