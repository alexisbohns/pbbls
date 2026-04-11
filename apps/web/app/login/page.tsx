import LoginForm from "@/components/auth/LoginForm"

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>
}) {
  const { error } = await searchParams
  const initialError =
    error === "auth_callback_failed" ? "Sign-in failed. Please try again." : null

  return <LoginForm initialError={initialError} />
}
