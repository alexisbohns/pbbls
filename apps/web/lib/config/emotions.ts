export type Emotion = { id: string; slug: string; name: string; label: string; color: string }

export const EMOTIONS: Emotion[] = [
  // Fear / Anxiety spectrum
  { id: "b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb", slug: "anxious",      name: "Anxiety",      label: "Unease about what might happen",            color: "#8B5CF6" },
  { id: "afd28421-b994-475c-ad49-75ecae570ee5", slug: "overwhelmed",   name: "Overwhelmed",  label: "Too much to handle at once",                color: "#8B5CF6" },
  { id: "8b2993f3-4043-7bdf-388d-c0e3d5c96f02", slug: "scared",        name: "Fear",         label: "Sensing danger or threat",                  color: "#8B5CF6" },
  { id: "93694d2b-41a7-4dfc-8582-7cce835ca9fe", slug: "stressed",      name: "Stressed",     label: "Pressure from demands exceeding capacity",  color: "#8B5CF6" },
  { id: "2d45e136-cee7-4bf1-b5fa-a3a79f26feab", slug: "worried",       name: "Worried",      label: "Dwelling on a specific concern",            color: "#8B5CF6" },
  // Sadness spectrum
  { id: "f76b1274-04ab-4a9c-858d-4da26f8d124d", slug: "disappointed",  name: "Disappointed", label: "Let down by unmet expectations",            color: "#60A5FA" },
  { id: "8a79a265-5dcd-4d42-b8de-8c1ff5d7e505", slug: "discouraged",   name: "Discouraged",  label: "Losing motivation to keep going",           color: "#60A5FA" },
  { id: "ce828ce7-78ad-4a39-bfe6-0c843cfb2838", slug: "drained",       name: "Drained",      label: "Emotionally or physically depleted",        color: "#60A5FA" },
  { id: "5d5e346a-c5c5-4fcb-99e9-2951213c3ff3", slug: "hopeless",      name: "Hopeless",     label: "Feeling nothing will improve",              color: "#60A5FA" },
  { id: "f99d4e11-5a81-101b-07a3-6fc89a7aa1d9", slug: "lonely",        name: "Loneliness",   label: "Feeling disconnected from others",          color: "#60A5FA" },
  { id: "075ec40d-f27c-033d-a498-850947e4e887", slug: "sad",           name: "Sadness",      label: "A heaviness from loss or longing",          color: "#60A5FA" },
  // Shame / Guilt spectrum
  { id: "0c98e05e-cf64-3b6f-929f-8bb6d0449b5f", slug: "ashamed",       name: "Shame",        label: "Feeling fundamentally flawed or exposed",   color: "#78716C" },
  { id: "52301a19-4b27-4432-9d0b-281f6341bb95", slug: "embarrassed",   name: "Embarrassed",  label: "Uncomfortable from a social misstep",       color: "#78716C" },
  { id: "648b1092-820c-b19a-8a27-07072b068724", slug: "guilty",        name: "Guilt",        label: "Regretting something you did or didn't do", color: "#78716C" },
  { id: "59f68361-3443-4352-a119-b0daaf864bca", slug: "indifferent",   name: "Indifferent",  label: "Not caring either way",                     color: "#78716C" },
  // Calm / Neutral spectrum
  { id: "5ff5cd1c-6739-6143-537f-85a2dbbd8841", slug: "calm",          name: "Calm",         label: "A quiet, centered stillness",               color: "#EEEEEE" },
  { id: "b0230f83-c4e4-4cdc-bf9f-2d650509658c", slug: "content",       name: "Content",      label: "Quietly satisfied with how things are",     color: "#EEEEEE" },
  { id: "b5c925f9-ed7d-5b87-2dc2-d3857e736235", slug: "grateful",      name: "Gratitude",    label: "Appreciating what you have or received",    color: "#EEEEEE" },
  { id: "b4102799-3d5f-45f2-bfa9-28d72fb89bec", slug: "hopeful",       name: "Hopeful",      label: "Believing things can get better",           color: "#EEEEEE" },
  { id: "21d79e4c-9482-40d3-bf73-53adfb452e5a", slug: "peaceful",      name: "Peaceful",     label: "Harmony with yourself and the world",       color: "#EEEEEE" },
  { id: "0312c804-7656-4f33-950c-a70a5b08b842", slug: "relieved",      name: "Relieved",     label: "Tension dissolving after a worry passes",   color: "#EEEEEE" },
  { id: "776d210e-29f3-4f14-acb3-9787518aa53d", slug: "satisfied",     name: "Satisfied",    label: "Pleased with an outcome or effort",         color: "#EEEEEE" },
  { id: "a9b0625b-8a77-06cf-ba9c-a348ab58b12c", slug: "surprised",     name: "Surprise",     label: "Caught off guard by the unexpected",        color: "#EEEEEE" },
  // Anger spectrum
  { id: "8a851a45-d1c5-92fd-fd50-098c4e86eaef", slug: "angry",         name: "Anger",        label: "A strong reaction against injustice or violation", color: "#EF4444" },
  { id: "25d98464-bc3d-417f-ae12-a3a996e5dee1", slug: "annoyed",       name: "Annoyed",      label: "Mildly bothered by something small",        color: "#EF4444" },
  { id: "332cabe9-c76d-d737-bba6-f2d7024de808", slug: "disgusted",     name: "Disgust",      label: "Strong aversion to something offensive",    color: "#EF4444" },
  { id: "fab5fa1f-ad54-4705-a205-f8a0b2b31806", slug: "frustrated",    name: "Frustrated",   label: "Blocked from reaching a goal",              color: "#EF4444" },
  { id: "df630582-0846-4ebf-be75-166595513840", slug: "irritated",     name: "Irritated",    label: "Bothered by repeated small annoyances",     color: "#EF4444" },
  { id: "c32faae1-46ac-411b-987f-e32e572259bb", slug: "jealous",       name: "Jealous",      label: "Wanting what someone else has",             color: "#EF4444" },
  // Confidence / Pride spectrum
  { id: "70e70f6a-237b-4e2d-8f4c-8043df889b36", slug: "brave",         name: "Brave",        label: "Facing fear with courage",                  color: "#F97316" },
  { id: "3e14e406-90e8-41fd-a967-30fa545c47dc", slug: "confident",     name: "Confident",    label: "Trusting your own ability",                 color: "#F97316" },
  { id: "ed9fbe9f-6a93-22bb-d5d5-35f42164c120", slug: "proud",         name: "Pride",        label: "Fulfilled by something you achieved",       color: "#F97316" },
  // Joy / Excitement spectrum
  { id: "50a68890-f6bb-e67a-6655-e84ff0b71f49", slug: "amazed",        name: "Awe",          label: "Struck by something vast or extraordinary", color: "#FACC15" },
  { id: "f14ca000-32c7-4cf3-9aa5-ad5dc84c5285", slug: "amused",        name: "Amused",       label: "Finding something funny or entertaining",   color: "#FACC15" },
  { id: "98bdc543-6b26-e1c8-da1d-50ae8b9973ff", slug: "excited",       name: "Excitement",   label: "High energy and anticipation for something ahead", color: "#FACC15" },
  { id: "f1b09d24-3c2e-414b-a751-fc91da51fcdb", slug: "happy",         name: "Happy",        label: "A warm sense that things are good",         color: "#FACC15" },
  { id: "3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c", slug: "joyful",        name: "Joy",          label: "Deep, radiant happiness",                   color: "#FACC15" },
  { id: "6b22ff42-67d4-ea3d-f35b-f71e49ae94cb", slug: "passionate",    name: "Passion",      label: "Intense drive and deep engagement",         color: "#FACC15" },
]
