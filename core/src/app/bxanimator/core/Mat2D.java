package app.bxanimator.core;

/**
 * A 2D affine matrix in the same layout as Android's {@code android.graphics.Matrix}
 * (values a, b, c, d, tx, ty) so it can be copied into the renderer without any
 * conversion. Points are transformed as:
 *
 * <pre>  x' = a*x + c*y + tx
 *  y' = b*x + d*y + ty</pre>
 */
public final class Mat2D {

    public float a = 1f, b = 0f, c = 0f, d = 1f, tx = 0f, ty = 0f;

    public Mat2D() {
    }

    public Mat2D(float a, float b, float c, float d, float tx, float ty) {
        set(a, b, c, d, tx, ty);
    }

    public void set(float a, float b, float c, float d, float tx, float ty) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.tx = tx;
        this.ty = ty;
    }

    public void set(Mat2D other) {
        set(other.a, other.b, other.c, other.d, other.tx, other.ty);
    }

    public static Mat2D identity() {
        return new Mat2D();
    }

    public static Mat2D translate(float x, float y) {
        return new Mat2D(1, 0, 0, 1, x, y);
    }

    public static Mat2D scale(float s) {
        return new Mat2D(s, 0, 0, s, 0, 0);
    }

    public static Mat2D scale(float sx, float sy) {
        return new Mat2D(sx, 0, 0, sy, 0, 0);
    }

    public static Mat2D rotate(float degrees) {
        float r = AnimMath.degToRad(degrees);
        float cos = (float) Math.cos(r);
        float sin = (float) Math.sin(r);
        return new Mat2D(cos, sin, -sin, cos, 0, 0);
    }

    public static Mat2D compose(float x, float y, float rotationDeg, float sx, float sy) {
        Mat2D m = translate(x, y);
        m.mul(rotate(rotationDeg));
        m.mul(scale(sx, sy));
        return m;
    }

    /** this = this * other (other is applied first to a point). */
    public Mat2D mul(Mat2D other) {
        float na = a * other.a + c * other.b;
        float nb = b * other.a + d * other.b;
        float nc = a * other.c + c * other.d;
        float nd = b * other.c + d * other.d;
        float ntx = a * other.tx + c * other.ty + tx;
        float nty = b * other.tx + d * other.ty + ty;
        set(na, nb, nc, nd, ntx, nty);
        return this;
    }

    public Mat2D copy() {
        return new Mat2D(a, b, c, d, tx, ty);
    }

    public float mapX(float x, float y) {
        return a * x + c * y + tx;
    }

    public float mapY(float x, float y) {
        return b * x + d * y + ty;
    }

    /** Rotation of the local Y axis in degrees, used by the pose hit test. */
    public float rotationDegrees() {
        return AnimMath.radToDeg((float) Math.atan2(b, a));
    }

    public float scaleX() {
        return (float) Math.hypot(a, b);
    }
}
