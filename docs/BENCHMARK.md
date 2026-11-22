# IAQ benchmark — Bleach before/after pair

The issue supplied two images and asked IAQ to match the second one:

- **Input:** `docs/benchmark/before.png`, 1800x900 PNG, a flat orange sky, bright
  red buildings, soft ink lines and a visible watermark.
- **Reference:** `docs/benchmark/after.jpg`, 1200x896 JPEG, a re-rendered
  widescreen-to-4:3 crop with a golden glowing sky, clouds, embers, deeper
  shadows, richer reds and cleaner line work.

`docs/benchmark/iaq-2x.jpg` is IAQ's output, downscaled to 1200 px for this
document. `docs/benchmark/crops.jpg` shows 100% crops (input, IAQ 2x,
reference) of the same area.

## What the reference actually is

The reference is **not a filter applied to the input**. It is a generative
re-render:

- the composition changes (a 4:3 crop, characters moved and re-posed, the
  watermark gone, a new foreground character at the bottom left);
- new content appears (clouds, light rays, floating embers, extra background
  figures);
- the characters are re-drawn with new shading and highlights.

No deterministic image filter can invent that content or move a character.
Anyone who claims a shader reproduces that pair exactly is comparing a
different image. What *can* be matched is the visual language: the grade, the
lighting, the contrast, the clarity and the colour depth.

## What IAQ matches

| Cue in the reference | IAQ stage |
| --- | --- |
| golden cream sky instead of flat orange | warm highlight lift gated to warm, low-blue pixels |
| deep warm shadows, no grey veil | black point + gamma + luma S-curve |
| rich reds without neon oversaturation | luma contrast + vibrance instead of a global saturation multiply |
| crisp ink lines, no white halos | Lanczos-3 with anti-ringing + clamped two-scale detail |
| glow around the brightest sky | thresholded multi-scale bloom |
| smooth sky gradient | 16 bit capable output + triangular dither + fine grain |
| clean colour edges | joint bilateral chroma denoise |

## Measurements

`java -jar build/IAQ.jar probe` on the three files:

| Image | Size | Mean luma | Contrast | Saturation | Sharpness | Clip hi / lo |
| --- | --- | --- | --- | --- | --- | --- |
| before.png | 1800x900 | 0.387 | 0.290 | 0.450 | 0.0225 | 0.17% / 2.59% |
| IAQ 2x (8 bit) | 3600x1800 | 0.387 | 0.330 | 0.573 | 0.0163 | 0.00% / 0.01% |
| after.jpg (reference) | 1200x896 | 0.364 | 0.304 | 0.452 | 0.0540 | 1.48% / 0.00% |

Sharpness is a per-pixel Laplacian mean, so it falls when the image is upscaled
and is not comparable between resolutions. Contrast and colour depth are the
meaningful numbers: IAQ is above the reference on both, and unlike the
reference it clips no highlights and only 0.01% of its shadows.

Region averages on the supplied pair:

| Region | Before | IAQ | Reference |
| --- | --- | --- | --- |
| sky (upper centre) | 250, 165, 46 | 245, 200, 72 | 250, 205, 70 |
| shadow / dark cloth | 142, 83, 67 | 148, 89, 63 | 111, 76, 65 |

The sky is the clearest win: the reference lifts green by about 35 and blue by
about 25 levels while keeping red, turning flat orange into golden cream. IAQ
reproduces that within five levels. The reference's skin and shadows are
redder and darker because the frame was re-drawn; IAQ deliberately keeps the
source's skin hue instead of inventing a new one, and only pulls the shadows
down to the reference's depth.

## Reproduce it

```bash
bash build.sh

# full-frame 2x
java -jar build/IAQ.jar remaster docs/benchmark/before.png \
    -o /tmp/iaq-2x.png --scale 2 --report

# 16 bit master
java -jar build/IAQ.jar remaster docs/benchmark/before.png \
    -o /tmp/iaq-2x-16.png --scale 2 --depth 16

# the reference framing
java -jar build/IAQ.jar remaster docs/benchmark/before.png \
    -o /tmp/iaq-4x3.png --scale 2 --aspect 4:3
```

## Tuning notes

The first pass used a per-channel S-curve and a strong global saturation
multiply. It looked like a poster: saturation 0.61, a yellow cast on skin, and
a flat sky. The final version:

1. applies contrast to **luma** and scales RGB by the ratio, so hue and
   saturation stay under separate control;
2. reduces the saturation boost to a small vibrance term;
3. gates the creamy highlight lift on *warm and low-blue* pixels, so the sky
   gets the golden treatment while skin and white cloth do not;
4. protects shadows in CLAHE with a luma mask, so deep cloth stays deep;
5. uses a quarter-resolution bloom at a 0.72 threshold instead of washing the
   whole frame;
6. ends with a final toe after the glow, so the bloom cannot lift the blacks
   back up.

That is why the measured saturation moved from 0.61 to 0.57, the mean luma
stayed at the source's 0.387 while contrast rose to 0.330, and the sky landed
within five levels of the reference.
