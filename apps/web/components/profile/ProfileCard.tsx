import type { Account, Profile } from "@/lib/types"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

interface ProfileCardProps {
  user: Account
  profile: Profile
}

export function ProfileCard({ user, profile }: ProfileCardProps) {
  const memberSince = new Intl.DateTimeFormat(undefined, {
    dateStyle: "long",
  }).format(new Date(user.created_at))

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">{profile.display_name}</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="space-y-3 text-sm">
          <div>
            <dt className="text-muted-foreground">Username</dt>
            <dd className="font-medium">@{user.username}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Member since</dt>
            <dd className="font-medium">{memberSince}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  )
}
