import { describe, expect, it } from "vitest"
import { exifCaptureDate } from "./exif-capture-date"

/**
 * Builds the smallest JPEG that carries an EXIF `DateTimeOriginal`: SOI, one
 * APP1 segment holding a TIFF header, an IFD0 pointing at an Exif sub-IFD, and
 * the date as an out-of-line ASCII value. Real camera files carry dozens of
 * other tags around this; the parser has to find the one it wants regardless.
 */
function jpegWithCaptureDate(
  value: string,
  options: { little?: boolean; inIfd0?: boolean; type?: number } = {},
): ArrayBuffer {
  const { little = false, inIfd0 = false, type = 2 } = options
  const ascii = new TextEncoder().encode(`${value}\0`)

  // TIFF block: header(8) + IFD0(2 + n*12 + 4) [+ sub-IFD] + the ASCII value.
  // IFD0 always holds exactly one entry here: either the date itself, or the
  // sub-IFD pointer that leads to it.
  const ifd0Size = 2 + 12 + 4
  const subIfdSize = inIfd0 ? 0 : 2 + 12 + 4
  const ifd0At = 8
  const subIfdAt = ifd0At + ifd0Size
  const valueAt = subIfdAt + subIfdSize
  const tiff = new DataView(new ArrayBuffer(valueAt + ascii.length))

  tiff.setUint16(0, little ? 0x4949 : 0x4d4d)
  tiff.setUint16(2, 0x002a, little)
  tiff.setUint32(4, ifd0At, little)

  const writeEntry = (at: number, tag: number, tagType: number, count: number, val: number) => {
    tiff.setUint16(at, tag, little)
    tiff.setUint16(at + 2, tagType, little)
    tiff.setUint32(at + 4, count, little)
    tiff.setUint32(at + 8, val, little)
  }

  tiff.setUint16(ifd0At, 1, little)
  if (inIfd0) {
    writeEntry(ifd0At + 2, 0x9003, type, ascii.length, valueAt)
  } else {
    // 0x8769 — the Exif sub-IFD pointer, where the tag normally lives.
    writeEntry(ifd0At + 2, 0x8769, 4, 1, subIfdAt)
    tiff.setUint16(subIfdAt, 1, little)
    writeEntry(subIfdAt + 2, 0x9003, type, ascii.length, valueAt)
    tiff.setUint32(subIfdAt + 2 + 12, 0, little)
  }
  tiff.setUint32(ifd0At + 2 + 12, 0, little)
  new Uint8Array(tiff.buffer).set(ascii, valueAt)

  const exif = new Uint8Array([0x45, 0x78, 0x69, 0x66, 0x00, 0x00])
  const payload = exif.length + tiff.byteLength
  const jpeg = new Uint8Array(2 + 2 + 2 + payload)
  const out = new DataView(jpeg.buffer)
  out.setUint16(0, 0xffd8) // SOI
  out.setUint16(2, 0xffe1) // APP1
  out.setUint16(4, 2 + payload) // segment length, including its own two bytes
  jpeg.set(exif, 6)
  jpeg.set(new Uint8Array(tiff.buffer), 6 + exif.length)
  return jpeg.buffer
}

describe("exifCaptureDate", () => {
  it("reads DateTimeOriginal out of the Exif sub-IFD", () => {
    const date = exifCaptureDate(jpegWithCaptureDate("2026:08:23 14:35:09"))
    expect(date).not.toBeNull()
    // EXIF carries no timezone, so the digits are read as local wall-clock time.
    expect(date!.getFullYear()).toBe(2026)
    expect(date!.getMonth()).toBe(7)
    expect(date!.getDate()).toBe(23)
    expect(date!.getHours()).toBe(14)
    expect(date!.getMinutes()).toBe(35)
    expect(date!.getSeconds()).toBe(9)
  })

  it("reads little-endian files, which is what phones actually write", () => {
    const date = exifCaptureDate(jpegWithCaptureDate("2026:01:02 03:04:05", { little: true }))
    expect(date?.getFullYear()).toBe(2026)
    expect(date?.getHours()).toBe(3)
  })

  it("falls back to IFD0 for encoders that skip the sub-IFD", () => {
    const date = exifCaptureDate(jpegWithCaptureDate("2025:12:31 23:59:58", { inIfd0: true }))
    expect(date?.getFullYear()).toBe(2025)
    expect(date?.getDate()).toBe(31)
  })

  it("returns null for a JPEG with no EXIF segment at all", () => {
    // SOI followed by a comment segment and nothing else.
    const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xfe, 0x00, 0x04, 0x00, 0x00])
    expect(exifCaptureDate(jpeg.buffer)).toBeNull()
  })

  it("returns null for a camera that never had its clock set", () => {
    expect(exifCaptureDate(jpegWithCaptureDate("0000:00:00 00:00:00"))).toBeNull()
  })

  it("returns null for a malformed date string", () => {
    expect(exifCaptureDate(jpegWithCaptureDate("not a timestamp!!!!"))).toBeNull()
  })

  it("returns null when the tag is not the ASCII type it must be", () => {
    expect(exifCaptureDate(jpegWithCaptureDate("2026:08:23 14:35:09", { type: 4 }))).toBeNull()
  })

  it("returns null for bytes that are not an image", () => {
    expect(exifCaptureDate(new TextEncoder().encode("plain text, no soi").buffer)).toBeNull()
    expect(exifCaptureDate(new ArrayBuffer(0))).toBeNull()
  })

  it("returns null for a truncated EXIF segment rather than throwing", () => {
    const full = new Uint8Array(jpegWithCaptureDate("2026:08:23 14:35:09"))
    // Cut the out-of-line value off: the offsets still point past the end.
    expect(exifCaptureDate(full.slice(0, full.length - 12).buffer)).toBeNull()
  })
})
