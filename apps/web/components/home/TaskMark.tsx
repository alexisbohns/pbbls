const BLOB = "M11.8 4.6 C17.2 4.5 21 7.4 20.9 11.6 C20.8 16.2 17 19.2 11.7 19.3 C7 19.3 3.3 16.4 3.3 12 C3.3 7.5 6.8 4.7 11.8 4.6 Z"

/**
 * A first-five task's mark (#1384, spec D12): a filled pebble with a tick when
 * done, an outlined one otherwise. Decorative: the row's words carry the state.
 */
export function TaskMark({ done }: { done: boolean }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-[26px] shrink-0">
      {done ? (
        <>
          <path d={BLOB} className="fill-m3-primary" />
          <path d="M8 12.2 L10.7 14.8 L16 9.3" fill="none" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" className="stroke-m3-on-primary" />
        </>
      ) : (
        <path d={BLOB} fill="none" strokeWidth={1.5} className="stroke-m3-outline" />
      )}
    </svg>
  )
}
