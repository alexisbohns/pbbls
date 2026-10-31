/**
 * The ready mark (#1308, D9): a quiet ring with its dot and "ready". It pulls
 * the set's ceremony; while any pull runs it cannot be pressed. Its spoken
 * label names the ceremony. iOS `ReadyButton`.
 */
export function ReadyMark({ text, label, disabled, onPull }: { text: string; label: string; disabled: boolean; onPull: () => void }) {
  return (
    <button
      type="button"
      onClick={onPull}
      disabled={disabled}
      aria-label={label}
      className="flex min-h-11 min-w-11 cursor-pointer items-center gap-1.5 rounded-full px-2.5 text-spine-ink outline-offset-2 focus-visible:outline-2 focus-visible:outline-spine-ink disabled:cursor-default disabled:opacity-50"
    >
      <span aria-hidden className="grid size-3 place-items-center rounded-full border border-spine-ink">
        <span className="size-1 rounded-full bg-spine-ink" />
      </span>
      <em className="text-sm font-[380]">{text}</em>
    </button>
  )
}
