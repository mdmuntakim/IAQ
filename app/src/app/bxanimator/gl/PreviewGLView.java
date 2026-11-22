package app.bxanimator.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** A full screen quad that shows a rendered frame through one GLSL effect. */
public final class PreviewGLView extends GLSurfaceView {

    private final Renderer renderer = new Renderer();

    public PreviewGLView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setBackgroundColor(0xFF000000);
    }

    public void setFrame(Bitmap bitmap) {
        renderer.setFrame(bitmap);
        requestRender();
    }

    public void setEffect(String id, float intensity, float[] params) {
        renderer.setEffect(id, intensity, params);
        requestRender();
    }

    public void setUniforms(float intensity, float[] params) {
        renderer.setUniforms(intensity, params);
        requestRender();
    }

    public void setTime(float time) {
        renderer.setTime(time);
        requestRender();
    }

    private static final class Renderer implements GLSurfaceView.Renderer {

        private final Object lock = new Object();
        private final FloatBuffer quad;

        private Bitmap bitmap;
        private boolean bitmapDirty;
        private String effectId = "grade";
        private float intensity = 0.6f;
        private float[] params = new float[]{1f, 1f, 0f, 0f};
        private float time;

        private int program;
        private int texture;
        private String compiledId;
        private int aPos;
        private int uTex;
        private int uRes;
        private int uTime;
        private int uIntensity;
        private int uParams;
        private int surfaceWidth = 1;
        private int surfaceHeight = 1;
        private boolean textureReady;

        Renderer() {
            float[] vertices = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
            quad = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            quad.put(vertices).position(0);
        }

        void setFrame(Bitmap bitmap) {
            synchronized (lock) {
                this.bitmap = bitmap;
                this.bitmapDirty = true;
            }
        }

        void setEffect(String id, float intensity, float[] params) {
            synchronized (lock) {
                this.effectId = id == null ? "grade" : id;
                this.intensity = intensity;
                this.params = params == null ? new float[]{1f, 1f, 0f, 0f} : params.clone();
            }
        }

        void setUniforms(float intensity, float[] params) {
            synchronized (lock) {
                this.intensity = intensity;
                if (params != null) this.params = params.clone();
            }
        }

        void setTime(float time) {
            synchronized (lock) {
                this.time = time;
            }
        }

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            texture = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            textureReady = true;
            program = 0;
            compiledId = null;
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            surfaceWidth = Math.max(1, width);
            surfaceHeight = Math.max(1, height);
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            Bitmap frame;
            boolean upload;
            String id;
            float strength;
            float[] values;
            float clock;
            synchronized (lock) {
                frame = bitmap;
                upload = bitmapDirty;
                bitmapDirty = false;
                id = effectId;
                strength = intensity;
                values = params.clone();
                clock = time;
            }
            if (frame == null) return;

            if (program == 0 || compiledId == null || !compiledId.equals(id)) {
                if (program != 0) GLES20.glDeleteProgram(program);
                program = build(id);
                compiledId = id;
                if (program == 0) program = build("grade");
                if (program == 0) return;
                aPos = GLES20.glGetAttribLocation(program, "aPos");
                uTex = GLES20.glGetUniformLocation(program, "uTex");
                uRes = GLES20.glGetUniformLocation(program, "uRes");
                uTime = GLES20.glGetUniformLocation(program, "uTime");
                uIntensity = GLES20.glGetUniformLocation(program, "uIntensity");
                uParams = GLES20.glGetUniformLocation(program, "uParams");
            }

            if (textureReady && upload) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame, 0);
            }

            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(uTex, 0);
            GLES20.glUniform2f(uRes, frame.getWidth(), frame.getHeight());
            GLES20.glUniform1f(uTime, clock);
            GLES20.glUniform1f(uIntensity, strength);
            GLES20.glUniform4f(uParams, values[0],
                    values.length > 1 ? values[1] : 0f,
                    values.length > 2 ? values[2] : 0f,
                    values.length > 3 ? values[3] : 0f);

            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(aPos);
        }

        private int build(String id) {
            int vertex = compile(GLES20.GL_VERTEX_SHADER, ShaderSource.vertex());
            int fragment = compile(GLES20.GL_FRAGMENT_SHADER, ShaderSource.fragment(id));
            if (vertex == 0 || fragment == 0) {
                if (vertex != 0) GLES20.glDeleteShader(vertex);
                if (fragment != 0) GLES20.glDeleteShader(fragment);
                return 0;
            }
            int result = GLES20.glCreateProgram();
            GLES20.glAttachShader(result, vertex);
            GLES20.glAttachShader(result, fragment);
            GLES20.glLinkProgram(result);
            int[] status = new int[1];
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (status[0] == 0) {
                GLES20.glDeleteProgram(result);
                return 0;
            }
            return result;
        }

        private int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                android.util.Log.w("BXAnimator", "shader compile failed: "
                        + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }
    }
}
