// ---------------------------------------------------------------------------
// Pebble image pipeline — mirror of iOS `ImagePipeline.process`
// (apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift).
//
// Produces two JPEG blobs sized to match iOS so both clients hit the same
// `pebbles-media` quotas (1.5 MB / image-jpeg only) and look identical in
// the detail view.
//
// Original: max edge 1024 px, target ≤1 MB,  start quality 0.85.
// Thumb:    max edge  420 px, target ≤300 KB, start quality 0.75.
// Quality steps down by 0.1 up to 3 times if the byte cap is exceeded.
// ---------------------------------------------------------------------------

const ORIGINAL_MAX_EDGE = 1024
const THUMB_MAX_EDGE = 420
const ORIGINAL_MAX_BYTES = 1_048_576
const THUMB_MAX_BYTES = 307_200
const ORIGINAL_START_QUALITY = 0.85
const THUMB_START_QUALITY = 0.75
const QUALITY_STEPS = 3
const QUALITY_DECREMENT = 0.1

export type ProcessedImage = {
  original: Blob
  thumb: Blob
}

export class ProcessPebbleImageError extends Error {
  constructor(public readonly kind: "decode" | "encode" | "tooLargeAfterResize") {
    super(`process-pebble-image: ${kind}`)
    this.name = "ProcessPebbleImageError"
  }
}

export async function processPebbleImage(file: File): Promise<ProcessedImage> {
  const bitmap = await loadBitmap(file)
  try {
    const [original, thumb] = await Promise.all([
      renderJpeg(bitmap, ORIGINAL_MAX_EDGE, ORIGINAL_START_QUALITY, ORIGINAL_MAX_BYTES),
      renderJpeg(bitmap, THUMB_MAX_EDGE, THUMB_START_QUALITY, THUMB_MAX_BYTES),
    ])
    return { original, thumb }
  } finally {
    bitmap.close()
  }
}

async function loadBitmap(file: File): Promise<ImageBitmap> {
  try {
    // `imageOrientation: "from-image"` bakes EXIF orientation into the bitmap,
    // matching iOS's `kCGImageSourceCreateThumbnailWithTransform`.
    return await createImageBitmap(file, { imageOrientation: "from-image" })
  } catch {
    throw new ProcessPebbleImageError("decode")
  }
}

async function renderJpeg(
  bitmap: ImageBitmap,
  maxEdge: number,
  startQuality: number,
  byteCap: number,
): Promise<Blob> {
  const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height))
  const width = Math.max(1, Math.round(bitmap.width * scale))
  const height = Math.max(1, Math.round(bitmap.height * scale))

  const canvas = document.createElement("canvas")
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext("2d")
  if (!ctx) throw new ProcessPebbleImageError("encode")
  ctx.drawImage(bitmap, 0, 0, width, height)

  let quality = startQuality
  for (let i = 0; i <= QUALITY_STEPS; i++) {
    const blob = await canvasToBlob(canvas, quality)
    if (blob.size <= byteCap) return blob
    quality -= QUALITY_DECREMENT
    if (quality <= 0.1) break
  }
  throw new ProcessPebbleImageError("tooLargeAfterResize")
}

function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob)
        else reject(new ProcessPebbleImageError("encode"))
      },
      "image/jpeg",
      quality,
    )
  })
}
