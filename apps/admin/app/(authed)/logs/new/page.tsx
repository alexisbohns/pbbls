import { LogForm } from "../_components/LogForm"
import { createLog } from "../actions"

export default function NewLogPage() {
  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">New log</h1>
        <p className="text-muted-foreground text-sm">Create a changelog entry or announcement.</p>
      </header>
      <LogForm log={null} action={createLog} submitLabel="Save draft" />
    </section>
  )
}
