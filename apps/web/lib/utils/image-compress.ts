// ---------------------------------------------------------------------------
// Image compression — client-side resize + JPEG encode via Canvas API.
// Pure utility, no React dependencies.
// ---------------------------------------------------------------------------

const MAX_DIMENSION = 800
const JPEG_QUALITY = 0.7

/**
 * Compress an image file to a JPEG base64 data URI.
 *
 * Scales the image so neither width nor height exceeds `MAX_DIMENSION`,
 * preserving aspect ratio, then encodes as JPEG at `JPEG_QUALITY`.
 */
export function compressImage(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()

    img.onload = () => {
      URL.revokeObjectURL(url)

      const { width, height } = img
      const scale = Math.min(1, MAX_DIMENSION / Math.max(width, height))
      const targetW = Math.round(width * scale)
      const targetH = Math.round(height * scale)

      const canvas = document.createElement("canvas")
      canvas.width = targetW
      canvas.height = targetH

      const ctx = canvas.getContext("2d")
      if (!ctx) {
        reject(new Error("Canvas 2D context unavailable"))
        return
      }

      ctx.drawImage(img, 0, 0, targetW, targetH)
      resolve(canvas.toDataURL("image/jpeg", JPEG_QUALITY))
    }

    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error("Failed to load image"))
    }

    img.src = url
  })
}
