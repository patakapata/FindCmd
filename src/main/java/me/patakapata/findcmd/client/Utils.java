package me.patakapata.findcmd.client;

import net.minecraft.client.util.GlAllocationUtils;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL43.*;

public class Utils {
    private Utils() {
    }

    private static final IntBuffer SIZE_BUFFER = GlAllocationUtils.allocateByteBuffer(4 * 10).asIntBuffer();
    private static final IntBuffer TYPE_BUFFER = GlAllocationUtils.allocateByteBuffer(4 * 10).asIntBuffer();

    public static String getAttributesInfo(int program) {
        StringBuilder builder = new StringBuilder();
        builder.append("// -------------------------------------------------- //\n");
        builder.append("Program ").append(program).append(" attributes info\n");
        int attributes = glGetProgrami(program, GL_ACTIVE_ATTRIBUTES);
        builder.append("There is ").append(attributes).append(" attributes\n");
        for (int i = 0; i < attributes; i++) {
            SIZE_BUFFER.clear();
            TYPE_BUFFER.clear();
            String msg = glGetActiveAttrib(program, i, 100, SIZE_BUFFER, TYPE_BUFFER);
            builder.append("Type: ").append(TYPE_BUFFER.get(0)).append(", Size: ").append(SIZE_BUFFER.get(0)).append(", Msg: ").append(msg).append('\n');
        }
        builder.append("// -------------------------------------------------- //\n");
        return builder.toString();
    }

    public static String getUniformsInfo(int program) {
        StringBuilder builder = new StringBuilder();
        builder.append("// -------------------------------------------------- //\n");
        builder.append("Program ").append(program).append(" uniforms info\n");
        int uniforms = glGetProgrami(program, GL_ACTIVE_UNIFORMS);
        builder.append("There is ").append(uniforms).append(" uniforms\n");
        for (int i = 0; i < uniforms; i++) {
            SIZE_BUFFER.clear();
            TYPE_BUFFER.clear();
            String msg = glGetActiveUniform(program, i, 100, SIZE_BUFFER, TYPE_BUFFER);
            builder.append("Type: ").append(TYPE_BUFFER.get(0)).append(", Size: ").append(SIZE_BUFFER.get(0)).append(", Msg: ").append(msg).append('\n');
        }
        builder.append("// -------------------------------------------------- //\n");
        return builder.toString();
    }
}
