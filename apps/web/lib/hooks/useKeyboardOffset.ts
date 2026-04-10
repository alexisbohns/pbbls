import { useEffect, useState } from "react"

/**
 * Returns the current virtual keyboard height by comparing the visual viewport
 * to the layout viewport. On iOS/Android, when the keyboard opens the visual
 * viewport shrinks while the layout viewport stays the same.
 */
export function useKeyboardOffset() {
  const [offset, setOffset] = useState(0)

  useEffect(() => {
    const vv = window.visualViewport
    if (!vv) return

    function update() {
      // The keyboard height is the difference between the full window height
      // and the visual viewport height, adjusted for any scroll offset.
      const keyboardHeight = window.innerHeight - vv!.height - vv!.offsetTop
      setOffset(Math.max(0, keyboardHeight))
    }

    vv.addEventListener("resize", update)
    vv.addEventListener("scroll", update)
    return () => {
      vv.removeEventListener("resize", update)
      vv.removeEventListener("scroll", update)
    }
  }, [])

  return offset
}
