export type CardType = { id: string; name: string; prompt: string }

export const CARD_TYPES: CardType[] = [
  { id: "free",      name: "Free",      prompt: "Write anything…" },
  { id: "feelings",  name: "Feelings",  prompt: "What did I feel?" },
  { id: "thoughts",  name: "Thoughts",  prompt: "What did I think?" },
  { id: "behaviour", name: "Behaviour", prompt: "What did I do?" },
]
