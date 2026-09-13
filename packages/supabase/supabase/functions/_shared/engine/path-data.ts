/**
 * Pebble Engine · Path-Data Grammar
 *
 * `glyphs.strokes` is jsonb with no CHECK constraint and is writable by its
 * owner through PostgREST, so every `d` reaching the compositor is untrusted
 * text. The compositor builds its SVG by string interpolation, which means a
 * `d` carrying a quote escapes its own attribute and injects markup that is
 * then stored in `pebbles.render_svg` and served to other people — including
 * anonymously, on the `/p/[id]` share page (#829).
 *
 * Escaping the quote would not be enough: the value would survive as a
 * *malformed path*, and the next reader of that column has no way to tell it
 * from real geometry. So this module does not escape — it **re-emits**. A `d`
 * is tokenized against the SVG path grammar and written back out from the
 * parsed tokens, so the only thing that can leave here is path data. Anything
 * the grammar does not recognize rejects the whole path rather than yielding a
 * partially-cleaned string, which is the failure mode that keeps biting
 * sanitizers that try to subtract the bad parts.
 *
 * Pure function. No DOM. No side effects.
 */

/** Argument count per path command. `A`/`a` are handled apart — two of their
 *  seven arguments are flags, not numbers. */
const ARITY: Record<string, number> = {
  M: 2, L: 2, T: 2,
  H: 1, V: 1,
  C: 6, S: 4, Q: 4,
  A: 7,
  Z: 0,
};

/** Largest coordinate magnitude a glyph may use. The carve editor works in a
 *  200×200 space; six orders of magnitude past that is already nonsense, and
 *  the bound keeps the bounding-box maths away from the range where rounding
 *  produces `Infinity` and the transform attribute stops being a number. */
const MAX_COORD = 1e6;

/** The complete set of path commands. Anything else — a quote, an angle
 *  bracket, a letter that is not a command — rejects the path. */
const COMMAND = /^[MmLlHhVvCcSsQqTtAaZz]$/;

/**
 * Re-emit an SVG path `d` from its parsed tokens.
 *
 * @param d — Untrusted path data from `glyphs.strokes`.
 * @returns — Equivalent path data containing nothing but commands and numbers,
 *            or `""` if the input is not a well-formed path. Callers drop the
 *            stroke on `""`; a glyph with one bad stroke loses that stroke, not
 *            its whole artwork.
 */
export function sanitizePathData(d: unknown): string {
  if (typeof d !== "string" || d.length === 0) return "";

  const out: string[] = [];
  let i = 0;
  // The command in force. SVG lets arguments repeat without restating it
  // ("M0 0 10 10" is a moveto then a lineto), so the parser has to carry it.
  let command = "";
  let implicit = "";

  const skipSeparators = (): void => {
    while (i < d.length && (d[i] === " " || d[i] === "," || d[i] === "\t" || d[i] === "\n" || d[i] === "\r" || d[i] === "\f")) {
      i += 1;
    }
  };

  /** Read one number, or `null` if the next token is not one. */
  const readNumber = (): number | null => {
    skipSeparators();
    const start = i;
    if (i < d.length && (d[i] === "+" || d[i] === "-")) i += 1;
    let digits = 0;
    while (i < d.length && d[i] >= "0" && d[i] <= "9") { i += 1; digits += 1; }
    if (i < d.length && d[i] === ".") {
      i += 1;
      while (i < d.length && d[i] >= "0" && d[i] <= "9") { i += 1; digits += 1; }
    }
    if (digits === 0) { i = start; return null; }
    if (i < d.length && (d[i] === "e" || d[i] === "E")) {
      const mantissaEnd = i;
      i += 1;
      if (i < d.length && (d[i] === "+" || d[i] === "-")) i += 1;
      let expDigits = 0;
      while (i < d.length && d[i] >= "0" && d[i] <= "9") { i += 1; expDigits += 1; }
      // A trailing "e" with no exponent is not part of the number — back up and
      // let the caller fail on it rather than swallowing the character.
      if (expDigits === 0) i = mantissaEnd;
    }
    const value = Number(d.slice(start, i));
    if (!Number.isFinite(value) || Math.abs(value) > MAX_COORD) return null;
    return value;
  };

  /** Read an arc flag: exactly the single character `0` or `1`. */
  const readFlag = (): number | null => {
    skipSeparators();
    if (i < d.length && (d[i] === "0" || d[i] === "1")) {
      const value = d[i] === "1" ? 1 : 0;
      i += 1;
      return value;
    }
    return null;
  };

  const readArgs = (cmd: string): number[] | null => {
    const upper = cmd.toUpperCase();
    if (upper === "A") {
      const rx = readNumber();
      const ry = readNumber();
      const rotation = readNumber();
      const largeArc = readFlag();
      const sweep = readFlag();
      const x = readNumber();
      const y = readNumber();
      if (rx === null || ry === null || rotation === null || largeArc === null ||
          sweep === null || x === null || y === null) return null;
      return [rx, ry, rotation, largeArc, sweep, x, y];
    }
    const args: number[] = [];
    for (let n = 0; n < ARITY[upper]; n += 1) {
      const value = readNumber();
      if (value === null) return null;
      args.push(value);
    }
    return args;
  };

  skipSeparators();
  // A path has to open with a moveto. Rejecting anything else here is what
  // stops a payload that leads with bare numbers or a stray letter.
  if (i >= d.length || (d[i] !== "M" && d[i] !== "m")) return "";

  while (i < d.length) {
    skipSeparators();
    if (i >= d.length) break;

    const char = d[i];
    if (COMMAND.test(char)) {
      command = char;
      i += 1;
      // After an explicit moveto, repeated argument sets are linetos.
      implicit = command === "M" ? "L" : command === "m" ? "l" : command;
    } else if (command === "") {
      return "";
    } else {
      // A repeated argument set for the command still in force.
      command = implicit;
    }

    const upper = command.toUpperCase();
    if (upper === "Z") {
      out.push(command);
      // `Z` takes no arguments, and nothing may follow it implicitly.
      implicit = "";
      command = "";
      continue;
    }

    const args = readArgs(command);
    if (args === null) return "";
    out.push(`${command}${args.map(format).join(" ")}`);
  }

  // A path that parsed to nothing but a command letter draws nothing; treat it
  // as empty rather than emitting a stroke the renderer will ignore.
  return out.length > 0 ? out.join(" ") : "";
}

/** Emit a number without exponent notation, which some SVG renderers mishandle. */
function format(n: number): string {
  const rounded = Math.round(n * 1000) / 1000;
  return Object.is(rounded, -0) ? "0" : String(rounded);
}
