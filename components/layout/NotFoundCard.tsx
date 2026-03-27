import Link from "next/link"

type NotFoundCardProps = {
  title: string
  description: string
  href: string
  linkText: string
}

export function NotFoundCard({ title, description, href, linkText }: NotFoundCardProps) {
  return (
    <section className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="text-sm text-muted-foreground">{description}</p>
      <Link
        href={href}
        className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        {linkText}
      </Link>
    </section>
  )
}
