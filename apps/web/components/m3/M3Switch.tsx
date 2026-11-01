"use client"

import { Switch as SwitchPrimitive } from "@base-ui/react/switch"
import { Checkmark } from "@pbbls/icons"
import { cn } from "@/lib/utils"

/**
 * An M3 switch (#1384, two-area spec D7, as Android #1437): a 52 × 32 track,
 * outlined off and filled `primary` on, a thumb that grows and carries a
 * check when on. Base UI's switch underneath (`role="switch"`, a hidden
 * input), so it is reached and toggled like any switch; wrapped in a label,
 * the whole row toggles it.
 */
export function M3Switch({ className, ...props }: SwitchPrimitive.Root.Props) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        "group/switch relative inline-flex h-8 w-13 shrink-0 items-center rounded-full border-2 border-m3-outline bg-m3-surface-container-highest outline-none transition-colors",
        "data-checked:border-m3-primary data-checked:bg-m3-primary",
        "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary",
        "data-disabled:cursor-not-allowed data-disabled:opacity-40 motion-reduce:transition-none",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="grid size-4 translate-x-1.5 place-items-center rounded-full bg-m3-outline transition-all data-checked:size-6 data-checked:translate-x-[1.25rem] data-checked:bg-m3-on-primary motion-reduce:transition-none">
        <Checkmark className="hidden size-4 text-m3-on-primary-container group-data-checked/switch:block" />
      </SwitchPrimitive.Thumb>
    </SwitchPrimitive.Root>
  )
}
