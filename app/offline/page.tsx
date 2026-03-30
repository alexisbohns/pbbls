import { Button } from "@/components/ui/button";

export default function OfflinePage() {
  return (
    <section className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <h1 className="text-2xl font-semibold">You&apos;re offline</h1>
      <p className="text-sm text-muted-foreground">
        This page isn&apos;t available offline. Connect to the internet and try
        again.
      </p>
      <Button variant="outline" render={<a href="/path" />}>
        Go to timeline
      </Button>
    </section>
  );
}
