import type { PebbleSize, PebbleValence } from "../types.ts";

import { shape as smallLowlight } from "./small-lowlight.ts";
import { shape as smallNeutral } from "./small-neutral.ts";
import { shape as smallHighlight } from "./small-highlight.ts";
import { shape as mediumLowlight } from "./medium-lowlight.ts";
import { shape as mediumNeutral } from "./medium-neutral.ts";
import { shape as mediumHighlight } from "./medium-highlight.ts";
import { shape as largeLowlight } from "./large-lowlight.ts";
import { shape as largeNeutral } from "./large-neutral.ts";
import { shape as largeHighlight } from "./large-highlight.ts";

const TABLE: Record<PebbleSize, Record<PebbleValence, string>> = {
  small:  { lowlight: smallLowlight,  neutral: smallNeutral,  highlight: smallHighlight  },
  medium: { lowlight: mediumLowlight, neutral: mediumNeutral, highlight: mediumHighlight },
  large:  { lowlight: largeLowlight,  neutral: largeNeutral,  highlight: largeHighlight  },
};

export function getShape(size: PebbleSize, valence: PebbleValence): string {
  return TABLE[size][valence];
}
