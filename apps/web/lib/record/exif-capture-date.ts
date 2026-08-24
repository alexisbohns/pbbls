/**
 * Reads the EXIF `DateTimeOriginal` out of a picked image's raw bytes.
 *
 * The web pipeline (`processPebbleImage`) decodes through `createImageBitmap`
 * and re-encodes off a canvas, which is deliberately metadata-free: by the time
 * bytes reach Storage the capture date is gone. That is correct for what we
 * upload and useless for what we want to ask, so the flow reads the date from
 * the `File` before handing it to the pipeline.
 *
 * Mirrors `ExifCaptureDate.from(_:)` on iOS. Pure and DOM-free — it walks the
 * JPEG marker segments itself rather than pulling in an EXIF library for one
 * tag, and returns null for anything it cannot read with confidence.
 */

const SOI = 0xffd8
const APP1 = 0xffe1
/** `Exif\0\0` — the APP1 payload prefix that marks an EXIF segment. */
const EXIF_HEADER = [0x45, 0x78, 0x69, 0x66, 0x00, 0x00]
const BIG_ENDIAN = 0x4d4d
const LITTLE_ENDIAN = 0x4949
const TIFF_MAGIC = 0x002a
const EXIF_IFD_POINTER = 0x8769
const DATE_TIME_ORIGINAL = 0x9003
/** ASCII — the only type `DateTimeOriginal` is ever stored as. */
const TYPE_ASCII = 2
/** `YYYY:MM:DD HH:MM:SS` plus its NUL terminator. */
const DATE_TIME_LENGTH = 20

export function exifCaptureDate(bytes: ArrayBuffer): Date | null {
  const view = new DataView(bytes)
  if (view.byteLength < 4 || view.getUint16(0) !== SOI) return null

  const tiffOffset = findExifTiffOffset(view)
  if (tiffOffset === null) return null

  try {
    return readDateTimeOriginal(view, tiffOffset)
  } catch {
    // A truncated or lying offset lands here. A photo whose metadata we cannot
    // read is not an error — the flow falls back to now.
    return null
  }
}

/** Walk the JPEG marker segments to the start of the EXIF TIFF header. */
function findExifTiffOffset(view: DataView): number | null {
  let offset = 2
  while (offset + 4 <= view.byteLength) {
    const marker = view.getUint16(offset)
    // Every segment marker starts with 0xFF; anything else means we have walked
    // off the segment chain (entropy-coded data) and there is no EXIF to find.
    if ((marker & 0xff00) !== 0xff00) return null
    const length = view.getUint16(offset + 2)
    if (length < 2) return null

    if (marker === APP1) {
      const payload = offset + 4
      if (payload + EXIF_HEADER.length > view.byteLength) return null
      const isExif = EXIF_HEADER.every((byte, i) => view.getUint8(payload + i) === byte)
      if (isExif) return payload + EXIF_HEADER.length
    }

    offset += 2 + length
  }
  return null
}

function readDateTimeOriginal(view: DataView, tiff: number): Date | null {
  const byteOrder = view.getUint16(tiff)
  if (byteOrder !== BIG_ENDIAN && byteOrder !== LITTLE_ENDIAN) return null
  const little = byteOrder === LITTLE_ENDIAN
  if (view.getUint16(tiff + 2, little) !== TIFF_MAGIC) return null

  const ifd0 = tiff + view.getUint32(tiff + 4, little)
  // `DateTimeOriginal` lives in the Exif sub-IFD, which IFD0 points at. Some
  // encoders also write it directly into IFD0, so try both.
  const exifIfd = findTag(view, ifd0, EXIF_IFD_POINTER, little)
  const raw =
    (exifIfd !== null ? readAscii(view, tiff, tiff + exifIfd, DATE_TIME_ORIGINAL, little) : null) ??
    readAscii(view, tiff, ifd0, DATE_TIME_ORIGINAL, little)

  return raw ? parseExifDateTime(raw) : null
}

/** The 4-byte value of a numeric tag in the given IFD, or null if absent. */
function findTag(
  view: DataView,
  ifd: number,
  tag: number,
  little: boolean,
): number | null {
  const count = view.getUint16(ifd, little)
  for (let i = 0; i < count; i++) {
    const entry = ifd + 2 + i * 12
    if (entry + 12 > view.byteLength) return null
    if (view.getUint16(entry, little) === tag) return view.getUint32(entry + 8, little)
  }
  return null
}

/** The ASCII value of a tag in the given IFD, or null if absent or mistyped. */
function readAscii(
  view: DataView,
  tiff: number,
  ifd: number,
  tag: number,
  little: boolean,
): string | null {
  if (ifd + 2 > view.byteLength) return null
  const count = view.getUint16(ifd, little)
  for (let i = 0; i < count; i++) {
    const entry = ifd + 2 + i * 12
    if (entry + 12 > view.byteLength) return null
    if (view.getUint16(entry, little) !== tag) continue
    if (view.getUint16(entry + 2, little) !== TYPE_ASCII) return null
    const length = view.getUint32(entry + 4, little)
    if (length < DATE_TIME_LENGTH) return null
    // An ASCII value longer than 4 bytes is stored out of line, at an offset
    // relative to the TIFF header rather than to the file.
    const value = tiff + view.getUint32(entry + 8, little)
    if (value + DATE_TIME_LENGTH > view.byteLength) return null
    let out = ""
    for (let c = 0; c < DATE_TIME_LENGTH - 1; c++) {
      out += String.fromCharCode(view.getUint8(value + c))
    }
    return out
  }
  return null
}

/**
 * `YYYY:MM:DD HH:MM:SS` → a Date in the viewer's own timezone.
 *
 * EXIF carries no zone, so the digits are wall-clock time where the photo was
 * taken. Reading them as local is the interpretation that puts a picture taken
 * at 9am on the timeline at 9am, which is what the user means by "when".
 */
function parseExifDateTime(raw: string): Date | null {
  const match = /^(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})$/.exec(raw.trim())
  if (!match) return null
  const [year, month, day, hour, minute, second] = match.slice(1).map(Number)
  // A camera that never had its clock set writes all zeroes; that is not a date.
  if (!year || !month || !day) return null
  const date = new Date(year, month - 1, day, hour, minute, second)
  return Number.isNaN(date.getTime()) ? null : date
}
