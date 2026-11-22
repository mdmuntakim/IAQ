package app.bxanimator.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import app.bxanimator.core.AnimMath;
import app.bxanimator.core.Bone;
import app.bxanimator.core.Mat2D;
import app.bxanimator.core.Part;
import app.bxanimator.core.Pose;
import app.bxanimator.core.Rig;

/**
 * Draws a puppet rig with the cel-shaded look of a modern TV anime: tapered
 * limbs, two tone shading, inked contours and a face built from eyes, brows and
 * a mouth that can be animated.
 */
public final class RigPainter {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path hairPath = new Path();
    private final RectF oval = new RectF();
    private final Matrix matrix = new Matrix();

    private float alpha = 1f;
    private int ink;
    private int hairColor;
    private String style = "hero";

    public void draw(Canvas canvas, Rig rig, Pose pose, float blink, float brow, float mouth,
                     float look, float opacity) {
        if (rig == null || pose == null) return;
        alpha = AnimMath.clamp(opacity, 0f, 1f);
        if (alpha <= 0.002f) return;
        ink = rig.ink;
        style = rig.style == null ? "hero" : rig.style;
        hairColor = 0xFF262B3A;
        for (Bone b : rig.bones) {
            if (b.part == Part.HAIR_BACK) hairColor = b.color;
        }
        for (Bone bone : rig.bones) {
            Mat2D m = pose.of(bone.id);
            matrix.setValues(new float[]{m.a, m.c, m.tx, m.b, m.d, m.ty, 0, 0, 1});
            canvas.save();
            canvas.concat(matrix);
            drawPart(canvas, rig, bone, blink, brow, mouth, look);
            canvas.restore();
        }
    }

    private void drawPart(Canvas canvas, Rig rig, Bone bone, float blink, float brow, float mouth, float look) {
        switch (bone.part) {
            case HEAD:
                drawHead(canvas, rig, bone, blink, brow, mouth, look);
                break;
            case HAIR_BACK:
                drawHairBack(canvas, bone);
                break;
            case TORSO:
                drawTorso(canvas, bone);
                break;
            case PELVIS:
                drawPelvis(canvas, bone);
                break;
            case NECK:
                drawCapsule(canvas, bone.length * 1.05f, bone.width, bone.width * 0.92f, bone.color, 2.6f);
                break;
            case UPPER_ARM:
                drawCapsule(canvas, bone.length, bone.width, bone.width * 0.82f, bone.color, 3f);
                break;
            case LOWER_ARM:
                drawCapsule(canvas, bone.length, bone.width, bone.width * 0.78f, bone.color, 3f);
                break;
            case HAND:
                drawHand(canvas, bone);
                break;
            case THIGH:
                drawCapsule(canvas, bone.length, bone.width, bone.width * 0.72f, bone.color, 3.2f);
                break;
            case SHIN:
                drawCapsule(canvas, bone.length, bone.width, bone.width * 0.68f, bone.color, 3.2f);
                break;
            case FOOT:
                drawFoot(canvas, bone);
                break;
            case SKIRT:
                drawSkirt(canvas, bone);
                break;
            case CAPE:
                if ("mecha".equals(style)) drawThruster(canvas, bone);
                else drawCape(canvas, bone);
                break;
            case SCARF:
                drawCape(canvas, bone);
                break;
            case SWORD:
                drawSword(canvas, bone);
                break;
            case TAIL:
            case WING:
            case PROP:
            default:
                drawProp(canvas, bone);
                break;
        }
    }

    // ------------------------------------------------------------- body

    private void drawCapsule(Canvas canvas, float length, float widthBase, float widthTip, int color, float inkWidth) {
        float hb = widthBase / 2f;
        float ht = Math.max(1.5f, widthTip / 2f);
        path.reset();
        path.moveTo(-hb, 0);
        path.lineTo(-ht, -length + ht);
        path.quadTo(-ht, -length, 0, -length);
        path.quadTo(ht, -length, ht, -length + ht);
        path.lineTo(hb, 0);
        path.quadTo(hb, hb, 0, hb);
        path.quadTo(-hb, hb, -hb, 0);
        path.close();

        fill.setColor(color(color));
        canvas.drawPath(path, fill);
        shade(canvas, color, widthBase);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(inkWidth);
        canvas.drawPath(path, stroke);
    }

    private void shade(Canvas canvas, int color, float width) {
        if (alpha < 0.4f) return;
        canvas.save();
        canvas.clipPath(path);
        fill.setColor(color(darken(color, 0.84f)));
        canvas.drawRect(width * 0.16f, -100000, width, 100000, fill);
        canvas.restore();
    }

    private void drawTorso(Canvas canvas, Bone bone) {
        float len = bone.length;
        float w = bone.width;
        path.reset();
        path.moveTo(-w * 0.40f, 0);
        path.cubicTo(-w * 0.50f, -len * 0.28f, -w * 0.56f, -len * 0.62f, -w * 0.44f, -len * 0.88f);
        path.quadTo(-w * 0.30f, -len * 1.02f, 0, -len * 1.0f);
        path.quadTo(w * 0.30f, -len * 1.02f, w * 0.44f, -len * 0.88f);
        path.cubicTo(w * 0.56f, -len * 0.62f, w * 0.50f, -len * 0.28f, w * 0.40f, 0);
        path.close();
        fill.setColor(color(bone.color));
        canvas.drawPath(path, fill);
        shade(canvas, bone.color, w);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3.2f);
        canvas.drawPath(path, stroke);

        // Collar and jacket seam.
        detail.setColor(color(darken(bone.color, 0.7f)));
        detail.setStyle(Paint.Style.STROKE);
        detail.setStrokeWidth(2.4f);
        detail.setStrokeCap(Paint.Cap.ROUND);
        Path seam = new Path();
        seam.moveTo(-w * 0.30f, -len * 0.86f);
        seam.quadTo(0, -len * 0.72f, w * 0.30f, -len * 0.86f);
        canvas.drawPath(seam, detail);
        if ("heroine".equals(style) || "chibi".equals(style)) {
            Path ribbon = new Path();
            ribbon.moveTo(-w * 0.10f, -len * 0.74f);
            ribbon.lineTo(0, -len * 0.66f);
            ribbon.lineTo(w * 0.10f, -len * 0.74f);
            ribbon.lineTo(w * 0.06f, -len * 0.62f);
            ribbon.lineTo(0, -len * 0.68f);
            ribbon.lineTo(-w * 0.06f, -len * 0.62f);
            ribbon.close();
            fill.setColor(color(darken(bone.color, 0.55f)));
            canvas.drawPath(ribbon, fill);
        }
    }

    private void drawPelvis(Canvas canvas, Bone bone) {
        float w = bone.width;
        float len = bone.length;
        path.reset();
        path.moveTo(-w * 0.44f, -len);
        path.lineTo(w * 0.44f, -len);
        path.cubicTo(w * 0.50f, -len * 0.5f, w * 0.46f, -len * 0.1f, w * 0.34f, 0);
        path.lineTo(-w * 0.34f, 0);
        path.cubicTo(-w * 0.46f, -len * 0.1f, -w * 0.50f, -len * 0.5f, -w * 0.44f, -len);
        path.close();
        fill.setColor(color(bone.color));
        canvas.drawPath(path, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3f);
        canvas.drawPath(path, stroke);
        detail.setColor(color(darken(bone.color, 0.75f)));
        detail.setStyle(Paint.Style.STROKE);
        detail.setStrokeWidth(2.2f);
        canvas.drawLine(-w * 0.2f, -len * 0.5f, w * 0.2f, -len * 0.5f, detail);
    }

    private void drawHand(Canvas canvas, Bone bone) {
        float r = bone.width * 0.5f;
        oval.set(-r, -bone.length - r * 0.2f, r, -bone.length + r * 1.4f);
        fill.setColor(color(bone.color));
        canvas.drawOval(oval, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(2.4f);
        canvas.drawOval(oval, stroke);
    }

    private void drawFoot(Canvas canvas, Bone bone) {
        float w = bone.width;
        path.reset();
        path.moveTo(-w * 0.45f, 0);
        path.quadTo(-w * 0.55f, -bone.length * 0.7f, -w * 0.2f, -bone.length);
        path.quadTo(0, -bone.length * 1.12f, w * 0.55f, -bone.length * 0.92f);
        path.quadTo(w * 0.7f, -bone.length * 0.4f, w * 0.45f, 0);
        path.close();
        fill.setColor(color(bone.color));
        canvas.drawPath(path, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(2.8f);
        canvas.drawPath(path, stroke);
    }

    private void drawSkirt(Canvas canvas, Bone bone) {
        float w = bone.width;
        float len = bone.length;
        path.reset();
        path.moveTo(-w * 0.34f, 0);
        path.lineTo(w * 0.34f, 0);
        path.cubicTo(w * 0.46f, len * 0.45f, w * 0.52f, len * 0.8f, w * 0.48f, len);
        for (int i = 4; i >= -4; i--) {
            float x = w * 0.48f * i / 4f;
            path.lineTo(x, len + (i % 2 == 0 ? 0 : len * 0.04f));
        }
        path.lineTo(-w * 0.34f, 0);
        path.close();
        fill.setColor(color(bone.color));
        canvas.drawPath(path, fill);
        shade(canvas, bone.color, w);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3f);
        canvas.drawPath(path, stroke);
        detail.setColor(color(darken(bone.color, 0.72f)));
        detail.setStyle(Paint.Style.STROKE);
        detail.setStrokeWidth(2f);
        for (int i = -2; i <= 2; i++) {
            canvas.drawLine(w * 0.07f * i, 0, w * 0.1f * i, len * 0.94f, detail);
        }
    }

    private void drawCape(Canvas canvas, Bone bone) {
        float w = bone.width;
        float len = bone.length;
        path.reset();
        path.moveTo(-w * 0.34f, 0);
        path.lineTo(w * 0.34f, 0);
        path.cubicTo(w * 0.48f, len * 0.3f, w * 0.44f, len * 0.7f, w * 0.40f, len);
        for (int i = 4; i >= -4; i--) {
            float x = w * 0.40f * i / 4f;
            float y = len + (i % 2 == 0 ? len * 0.05f : -len * 0.02f);
            path.lineTo(x, y);
        }
        path.lineTo(-w * 0.34f, 0);
        path.close();
        fill.setColor(color(darken(bone.color, 0.92f)));
        canvas.drawPath(path, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3f);
        canvas.drawPath(path, stroke);
        detail.setColor(color(darken(bone.color, 0.65f)));
        detail.setStyle(Paint.Style.STROKE);
        detail.setStrokeWidth(2f);
        canvas.drawLine(-w * 0.2f, len * 0.15f, -w * 0.24f, len * 0.9f, detail);
        canvas.drawLine(w * 0.2f, len * 0.15f, w * 0.24f, len * 0.9f, detail);
    }

    private void drawThruster(Canvas canvas, Bone bone) {
        float w = bone.width;
        float len = bone.length;
        fill.setColor(color(darken(bone.color, 0.9f)));
        canvas.drawRoundRect(new RectF(-w * 0.42f, 0, w * 0.42f, len * 0.8f), w * 0.08f, w * 0.08f, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3f);
        canvas.drawRoundRect(new RectF(-w * 0.42f, 0, w * 0.42f, len * 0.8f), w * 0.08f, w * 0.08f, stroke);
        fill.setColor(color(0xFF4DE1FF));
        canvas.drawRoundRect(new RectF(-w * 0.3f, len * 0.62f, -w * 0.06f, len * 0.8f), 6, 6, fill);
        canvas.drawRoundRect(new RectF(w * 0.06f, len * 0.62f, w * 0.3f, len * 0.8f), 6, 6, fill);
    }

    private void drawSword(Canvas canvas, Bone bone) {
        float len = bone.length;
        detail.setStyle(Paint.Style.FILL);
        detail.setColor(color(0xFF3A2A1E));
        canvas.drawRoundRect(new RectF(-7, -len * 0.14f, 7, 0), 4, 4, detail);
        detail.setColor(color(0xFFB8925A));
        canvas.drawRect(-30, -len * 0.18f, 30, -len * 0.14f, detail);

        path.reset();
        path.moveTo(-10, -len * 0.2f);
        path.lineTo(-5, -len * 0.94f);
        path.lineTo(0, -len);
        path.lineTo(5, -len * 0.94f);
        path.lineTo(10, -len * 0.2f);
        path.close();
        fill.setColor(color(0xFFD8E2F0));
        canvas.drawPath(path, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(2.2f);
        canvas.drawPath(path, stroke);
        detail.setColor(color(0x99FFFFFF));
        detail.setStyle(Paint.Style.STROKE);
        detail.setStrokeWidth(2f);
        canvas.drawLine(0, -len * 0.28f, 0, -len * 0.9f, detail);
    }

    private void drawProp(Canvas canvas, Bone bone) {
        if ("mecha".equals(style)) {
            float w = bone.width;
            float len = bone.length;
            fill.setColor(color(bone.color));
            canvas.drawRoundRect(new RectF(-w * 0.5f, -len, w * 0.5f, 0), w * 0.15f, w * 0.15f, fill);
            stroke.setColor(color(ink));
            stroke.setStrokeWidth(2.6f);
            canvas.drawRoundRect(new RectF(-w * 0.5f, -len, w * 0.5f, 0), w * 0.15f, w * 0.15f, stroke);
            detail.setColor(color(0x664DE1FF));
            detail.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(-w * 0.28f, -len * 0.8f, w * 0.28f, -len * 0.4f), 6, 6, detail);
        } else {
            drawCapsule(canvas, bone.length, bone.width, bone.width * 0.8f, bone.color, 2.8f);
        }
    }

    // ------------------------------------------------------------- head

    private void drawHead(Canvas canvas, Rig rig, Bone bone, float blink, float brow, float mouth, float look) {
        float len = bone.length;
        float w = bone.width;
        if ("mecha".equals(style)) {
            drawMechaHead(canvas, bone);
            return;
        }
        float chinY = 0;
        float faceH = len;

        // Ears
        oval.set(-w * 0.56f, -faceH * 0.62f, -w * 0.42f, -faceH * 0.38f);
        fill.setColor(color(bone.color));
        canvas.drawOval(oval, fill);
        oval.set(w * 0.42f, -faceH * 0.62f, w * 0.56f, -faceH * 0.38f);
        canvas.drawOval(oval, fill);

        // Face silhouette: rounded cranium, tapered jaw, pointed chin.
        path.reset();
        path.moveTo(0, chinY);
        path.cubicTo(-w * 0.22f, -faceH * 0.02f, -w * 0.40f, -faceH * 0.14f, -w * 0.46f, -faceH * 0.34f);
        path.cubicTo(-w * 0.52f, -faceH * 0.56f, -w * 0.50f, -faceH * 0.82f, -w * 0.32f, -faceH * 0.93f);
        path.cubicTo(-w * 0.16f, -faceH * 1.01f, w * 0.16f, -faceH * 1.01f, w * 0.32f, -faceH * 0.93f);
        path.cubicTo(w * 0.50f, -faceH * 0.82f, w * 0.52f, -faceH * 0.56f, w * 0.46f, -faceH * 0.34f);
        path.cubicTo(w * 0.40f, -faceH * 0.14f, w * 0.22f, -faceH * 0.02f, 0, chinY);
        path.close();
        fill.setColor(color(bone.color));
        canvas.drawPath(path, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3f);
        canvas.drawPath(path, stroke);

        // Neck shadow under the jaw.
        detail.setColor(color(darken(bone.color, 0.86f)));
        detail.setStyle(Paint.Style.FILL);
        oval.set(-w * 0.16f, -faceH * 0.08f, w * 0.16f, -faceH * 0.02f);
        canvas.drawOval(oval, detail);

        drawFace(canvas, rig, bone, blink, brow, mouth, look);
        drawBangs(canvas, bone);
    }

    private void drawFace(Canvas canvas, Rig rig, Bone bone, float blink, float brow, float mouth, float look) {
        float len = bone.length;
        float w = bone.width;
        float eyeY = -len * 0.52f;
        float eyeDX = w * 0.215f;
        float eyeW = w * ("chibi".equals(style) ? 0.24f : 0.185f);
        float eyeH = len * ("chibi".equals(style) ? 0.115f : 0.085f);
        float open = 1f - AnimMath.clamp(blink, 0f, 1f) * 0.95f;
        float lookX = AnimMath.clamp(look, -1f, 1f) * eyeW * 0.22f;
        int white = color(mixColor(0xFFFFFFFF, bone.color, 0.12f));

        for (int side = -1; side <= 1; side += 2) {
            float cx = side * eyeDX;
            float h = Math.max(0.6f, eyeH * open);
            oval.set(cx - eyeW / 2f, eyeY - h, cx + eyeW / 2f, eyeY + h);
            eyeWhite.setColor(white);
            canvas.drawOval(oval, eyeWhite);

            if (open > 0.25f) {
                float irisR = Math.min(eyeW * 0.44f, h * 0.95f);
                detail.setStyle(Paint.Style.FILL);
                detail.setColor(color(rig.eye));
                canvas.drawCircle(cx + lookX, eyeY + h * 0.05f, irisR, detail);
                detail.setColor(color(darken(rig.eye, 0.55f)));
                canvas.drawCircle(cx + lookX, eyeY + h * 0.1f, irisR * 0.5f, detail);
                detail.setColor(color(0xFFFFFFFF));
                canvas.drawCircle(cx + lookX - irisR * 0.32f, eyeY - h * 0.32f, Math.max(1.2f, irisR * 0.22f), detail);
            }

            // Upper lash line, thicker and winging outward like anime eyes.
            stroke.setColor(color(ink));
            stroke.setStrokeWidth(Math.max(2.2f, eyeH * 0.34f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            Path lash = new Path();
            lash.moveTo(cx - eyeW * 0.52f, eyeY - h * 0.55f);
            lash.quadTo(cx, eyeY - h * 1.15f, cx + eyeW * 0.52f, eyeY - h * 0.62f);
            canvas.drawPath(lash, stroke);
            if (open <= 0.25f) {
                canvas.drawLine(cx - eyeW * 0.42f, eyeY, cx + eyeW * 0.42f, eyeY, stroke);
            }

            // Brow
            float browLift = -eyeH * 2.1f;
            float innerDrop = AnimMath.clamp(brow, -1f, 1f) * eyeH * 0.9f;
            float innerX = cx - side * eyeW * 0.45f;
            float outerX = cx + side * eyeW * 0.6f;
            float innerY = eyeY + browLift + innerDrop;
            float outerY = eyeY + browLift - Math.abs(brow) * eyeH * 0.25f;
            stroke.setStrokeWidth(Math.max(2f, eyeH * 0.24f));
            canvas.drawLine(innerX, innerY, outerX, outerY, stroke);
        }

        // Nose
        detail.setStyle(Paint.Style.STROKE);
        detail.setColor(color(darken(bone.color, 0.72f)));
        detail.setStrokeWidth(1.8f);
        Path nose = new Path();
        nose.moveTo(-w * 0.02f, -len * 0.38f);
        nose.quadTo(w * 0.03f, -len * 0.35f, -w * 0.01f, -len * 0.32f);
        canvas.drawPath(nose, detail);

        // Mouth: a line at rest, opens into an ellipse.
        float mouthY = -len * 0.26f;
        float mouthW = w * 0.14f;
        float openMouth = AnimMath.clamp(mouth, 0f, 1f);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(2f);
        if (openMouth > 0.18f) {
            detail.setStyle(Paint.Style.FILL);
            detail.setColor(color(darken(0xFF7A2E38, 0.9f)));
            oval.set(-mouthW, mouthY - openMouth * len * 0.055f, mouthW, mouthY + openMouth * len * 0.075f);
            canvas.drawOval(oval, detail);
            detail.setColor(color(0xFFE57A86));
            oval.set(-mouthW * 0.55f, mouthY + openMouth * len * 0.02f, mouthW * 0.55f, mouthY + openMouth * len * 0.055f);
            canvas.drawOval(oval, detail);
        } else {
            Path smile = new Path();
            smile.moveTo(-mouthW, mouthY);
            smile.quadTo(0, mouthY + len * 0.022f, mouthW, mouthY);
            canvas.drawPath(smile, stroke);
        }
    }

    private void drawBangs(Canvas canvas, Bone bone) {
        float len = bone.length;
        float w = bone.width;
        hairPath.reset();
        if ("chibi".equals(style)) {
            hairPath.moveTo(-w * 0.52f, -len * 0.62f);
            hairPath.cubicTo(-w * 0.54f, -len * 0.98f, w * 0.54f, -len * 0.98f, w * 0.52f, -len * 0.62f);
            hairPath.cubicTo(w * 0.42f, -len * 0.52f, w * 0.3f, -len * 0.7f, w * 0.2f, -len * 0.58f);
            hairPath.cubicTo(w * 0.1f, -len * 0.48f, -w * 0.06f, -len * 0.66f, -w * 0.18f, -len * 0.55f);
            hairPath.cubicTo(-w * 0.3f, -len * 0.46f, -w * 0.4f, -len * 0.6f, -w * 0.52f, -len * 0.62f);
        } else if ("heroine".equals(style)) {
            hairPath.moveTo(-w * 0.54f, -len * 0.42f);
            hairPath.cubicTo(-w * 0.6f, -len * 0.9f, -w * 0.1f, -len * 1.04f, w * 0.36f, -len * 0.92f);
            hairPath.cubicTo(w * 0.56f, -len * 0.84f, w * 0.6f, -len * 0.58f, w * 0.52f, -len * 0.36f);
            hairPath.cubicTo(w * 0.38f, -len * 0.56f, w * 0.24f, -len * 0.7f, w * 0.06f, -len * 0.74f);
            hairPath.cubicTo(-w * 0.14f, -len * 0.78f, -w * 0.3f, -len * 0.6f, -w * 0.54f, -len * 0.42f);
        } else if ("villain".equals(style)) {
            hairPath.moveTo(-w * 0.56f, -len * 0.5f);
            hairPath.lineTo(-w * 0.62f, -len * 1.0f);
            hairPath.lineTo(-w * 0.3f, -len * 0.82f);
            hairPath.lineTo(-w * 0.16f, -len * 1.06f);
            hairPath.lineTo(w * 0.02f, -len * 0.84f);
            hairPath.lineTo(w * 0.22f, -len * 1.04f);
            hairPath.lineTo(w * 0.38f, -len * 0.8f);
            hairPath.lineTo(w * 0.62f, -len * 0.92f);
            hairPath.lineTo(w * 0.54f, -len * 0.46f);
            hairPath.cubicTo(w * 0.3f, -len * 0.66f, -w * 0.3f, -len * 0.66f, -w * 0.56f, -len * 0.5f);
        } else {
            hairPath.moveTo(-w * 0.52f, -len * 0.58f);
            hairPath.cubicTo(-w * 0.58f, -len * 0.95f, -w * 0.05f, -len * 1.06f, w * 0.5f, -len * 0.8f);
            hairPath.lineTo(w * 0.56f, -len * 0.52f);
            hairPath.lineTo(w * 0.3f, -len * 0.66f);
            hairPath.lineTo(w * 0.24f, -len * 0.44f);
            hairPath.lineTo(w * 0.06f, -len * 0.7f);
            hairPath.lineTo(-w * 0.08f, -len * 0.46f);
            hairPath.lineTo(-w * 0.24f, -len * 0.72f);
            hairPath.lineTo(-w * 0.38f, -len * 0.5f);
            hairPath.close();
        }
        hairPath.close();
        fill.setColor(color(hairColor));
        canvas.drawPath(hairPath, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(2.8f);
        canvas.drawPath(hairPath, stroke);
        if ("heroine".equals(style)) {
            // Long side locks in front of the shoulders.
            Path lock = new Path();
            lock.moveTo(-w * 0.5f, -len * 0.62f);
            lock.cubicTo(-w * 0.62f, -len * 0.1f, -w * 0.56f, len * 0.5f, -w * 0.44f, len * 0.86f);
            lock.lineTo(-w * 0.3f, len * 0.8f);
            lock.cubicTo(-w * 0.38f, len * 0.4f, -w * 0.4f, -len * 0.1f, -w * 0.34f, -len * 0.5f);
            lock.close();
            fill.setColor(color(hairColor));
            canvas.drawPath(lock, fill);
            canvas.drawPath(lock, stroke);
            lock.reset();
            lock.moveTo(w * 0.5f, -len * 0.62f);
            lock.cubicTo(w * 0.62f, -len * 0.1f, w * 0.56f, len * 0.5f, w * 0.44f, len * 0.86f);
            lock.lineTo(w * 0.3f, len * 0.8f);
            lock.cubicTo(w * 0.38f, len * 0.4f, w * 0.4f, -len * 0.1f, w * 0.34f, -len * 0.5f);
            lock.close();
            canvas.drawPath(lock, fill);
            canvas.drawPath(lock, stroke);
        }
    }

    private void drawHairBack(Canvas canvas, Bone bone) {
        float len = bone.length;
        float w = bone.width;
        if ("mecha".equals(style)) return;
        if ("heroine".equals(style) || "villain".equals(style)) {
            path.reset();
            path.moveTo(-w * 0.5f, -len * 0.62f);
            path.cubicTo(-w * 0.72f, -len * 0.2f, -w * 0.62f, len * 0.55f, -w * 0.5f, len * 0.92f);
            path.lineTo(w * 0.5f, len * 0.92f);
            path.cubicTo(w * 0.62f, len * 0.55f, w * 0.72f, -len * 0.2f, w * 0.5f, -len * 0.62f);
            path.cubicTo(w * 0.3f, -len * 0.95f, -w * 0.3f, -len * 0.95f, -w * 0.5f, -len * 0.62f);
            path.close();
            fill.setColor(color(darken(hairColor, 0.9f)));
            canvas.drawPath(path, fill);
            stroke.setColor(color(ink));
            stroke.setStrokeWidth(3f);
            canvas.drawPath(path, stroke);
            detail.setColor(color(darken(hairColor, 0.7f)));
            detail.setStyle(Paint.Style.STROKE);
            detail.setStrokeWidth(2f);
            canvas.drawLine(-w * 0.2f, -len * 0.4f, -w * 0.26f, len * 0.7f, detail);
            canvas.drawLine(w * 0.2f, -len * 0.4f, w * 0.26f, len * 0.7f, detail);
            return;
        }
        oval.set(-w * 0.54f, -len * 0.9f, w * 0.54f, -len * 0.05f);
        fill.setColor(color(hairColor));
        canvas.drawOval(oval, fill);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3f);
        canvas.drawOval(oval, stroke);
        // Spikes around the crown.
        Path spikes = new Path();
        for (int i = -3; i <= 3; i++) {
            float x = w * 0.16f * i;
            spikes.moveTo(x - w * 0.1f, -len * 0.7f);
            spikes.lineTo(x + w * 0.02f, -len * (1.02f + (i % 2 == 0 ? 0.08f : 0.02f)));
            spikes.lineTo(x + w * 0.12f, -len * 0.68f);
        }
        fill.setColor(color(hairColor));
        canvas.drawPath(spikes, fill);
        canvas.drawPath(spikes, stroke);
    }

    private void drawMechaHead(Canvas canvas, Bone bone) {
        float len = bone.length;
        float w = bone.width;
        path.reset();
        path.moveTo(-w * 0.34f, 0);
        path.lineTo(-w * 0.5f, -len * 0.45f);
        path.lineTo(-w * 0.34f, -len * 0.86f);
        path.lineTo(0, -len);
        path.lineTo(w * 0.34f, -len * 0.86f);
        path.lineTo(w * 0.5f, -len * 0.45f);
        path.lineTo(w * 0.34f, 0);
        path.close();
        fill.setColor(color(bone.color));
        canvas.drawPath(path, fill);
        shade(canvas, bone.color, w);
        stroke.setColor(color(ink));
        stroke.setStrokeWidth(3f);
        canvas.drawPath(path, stroke);

        fill.setColor(color(0xFF0B1018));
        canvas.drawRoundRect(new RectF(-w * 0.4f, -len * 0.66f, w * 0.4f, -len * 0.5f), 6, 6, fill);
        fill.setColor(color(0xFF4DE1FF));
        canvas.drawRoundRect(new RectF(-w * 0.34f, -len * 0.62f, w * 0.34f, -len * 0.55f), 4, 4, fill);
        stroke.setColor(color(0xFF1B2A38));
        stroke.setStrokeWidth(2f);
        canvas.drawLine(0, -len * 0.42f, 0, -len * 0.14f, stroke);
        canvas.drawLine(-w * 0.2f, -len * 0.24f, w * 0.2f, -len * 0.24f, stroke);

        stroke.setColor(color(ink));
        stroke.setStrokeWidth(2.6f);
        canvas.drawLine(w * 0.3f, -len * 0.9f, w * 0.5f, -len * 1.1f, stroke);
        fill.setColor(color(0xFFF2A03D));
        canvas.drawCircle(w * 0.5f, -len * 1.12f, 5, fill);
    }

    // ------------------------------------------------------------- helpers

    private int color(int base) {
        int a = (int) (Color.alpha(base) * alpha);
        return (base & 0x00FFFFFF) | (a << 24);
    }

    private static int darken(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.argb(Color.alpha(color), Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    private static int mixColor(int a, int b, float t) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.argb(255, r, g, bl);
    }
}
