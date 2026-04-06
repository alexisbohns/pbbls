import Link from "next/link"
import { Button } from "@/components/ui/button"
import { ChevronLeft } from "lucide-react"

export function BackPath() {
    return (
        <Button variant="outline" className="hidden md:flex mb-5 self-end" render={<Link href="/path" />}>
            <ChevronLeft />
            Back to path
        </Button>
    )
}