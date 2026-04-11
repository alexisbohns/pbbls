export type CardType = { id: string; name: string; prompt: string }

export const CARD_TYPES: CardType[] = [
  { id: "d1588ff1-0e61-e3d9-4945-8bc55d753687", name: "Free",      prompt: "Write anything…" },
  { id: "f2f31377-d3ca-25d6-c712-2c88f6f7d8a1", name: "Feelings",  prompt: "What did I feel?" },
  { id: "75ae28b4-f5ee-5749-7b29-27c232c56ff8", name: "Thoughts",  prompt: "What did I think?" },
  { id: "635248c5-180c-1734-242a-b637f408325b", name: "Behaviour", prompt: "What did I do?" },
]
