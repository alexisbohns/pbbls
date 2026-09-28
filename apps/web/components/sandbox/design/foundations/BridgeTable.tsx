import { M3_BRIDGE } from "@/lib/theme/m3-bridge"

function Chip({ color }: { color: string }) {
  return <span aria-hidden className="size-4 shrink-0 rounded-sm border border-border" style={{ background: color }} />
}

export function BridgeTable() {
  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-heading text-xl">Bridge</h3>
      <p className="max-w-2xl text-sm text-muted-foreground">
        Each app token and the M3 role it points at under <code>.m3</code> (globals.css). A tuned component
        skips the bridge and reads the role directly.
      </p>
      <table className="w-full max-w-xl text-sm">
        <thead>
          <tr className="text-left text-xs text-muted-foreground">
            <th className="py-1 font-medium">App token</th>
            <th className="py-1 font-medium">M3 role</th>
          </tr>
        </thead>
        <tbody>
          {M3_BRIDGE.map(([token, role]) => (
            <tr key={token} className="border-t border-border">
              <td className="py-1.5">
                <span className="inline-flex items-center gap-2">
                  <Chip color={`var(--${token})`} />
                  <code>--{token}</code>
                </span>
              </td>
              <td className="py-1.5">
                <span className="inline-flex items-center gap-2">
                  <Chip color={`var(--m3-${role})`} />
                  <code>{role}</code>
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
