package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class TransformFeedbackProgram {

    private static final String[] VARYINGS = { "tfPosition", "tfColor", "tfUV" };
    private static final String FRAGMENT = "#version 150\nout vec4 fragColor;\nvoid main() {\n    fragColor = vec4(1.0);\n}\n";
    private static final String[] RING_ATTRIBUTES = { "Ring0", "Ring1", "Ring2", "Ring3", "Ring4", "Ring5", "Ring6", "Ring7" };

    final int id;
    final int[] ring = new int[RING_ATTRIBUTES.length];

    private TransformFeedbackProgram(int id) {
        this.id = id;
        for (int i = 0; i < RING_ATTRIBUTES.length; i++) ring[i] = GL20.glGetAttribLocation(id, RING_ATTRIBUTES[i]);
    }

    int uniform(String name) {
        return GL20.glGetUniformLocation(id, name);
    }

    static boolean supported() {
        return GL.getCapabilities().OpenGL33;
    }

    private static String source(String name) throws IOException {
        var manager = Minecraft.getInstance().getResourceManager();
        try (var stream = manager.getResourceOrThrow(ModularMCLibUtils.id("shaders/effect/" + name)).open()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Nullable
    static TransformFeedbackProgram load(String vertex) {
        try {
            String code = source(vertex).replace("//COMMON", source("ring_common.glsl"));
            int vertexShader = compile(GL20.GL_VERTEX_SHADER, code, vertex);
            int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT, "feedback.fsh");
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL30.glTransformFeedbackVaryings(program, VARYINGS, GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(program);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                ModularMCLibUtils.LOGGER.error("反馈程序 {} 链接失败，驱动信息为 {}", vertex, GL20.glGetProgramInfoLog(program, 32768));
                GL20.glDeleteProgram(program);
                return null;
            }
            return new TransformFeedbackProgram(program);
        } catch (IOException e) {
            ModularMCLibUtils.LOGGER.error("反馈程序 {} 加载失败", vertex, e);
            return null;
        }
    }

    private static int compile(int type, String code, String name) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, code);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            ModularMCLibUtils.LOGGER.error("着色器 {} 编译失败，驱动信息为 {}", name, GL20.glGetShaderInfoLog(shader, 32768));
        }
        return shader;
    }
}
