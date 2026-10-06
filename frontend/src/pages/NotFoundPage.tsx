import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-2 px-4 text-center">
      <p className="text-sm font-semibold text-emerald-700">404</p>
      <h1 className="text-2xl font-semibold tracking-tight">Page not found</h1>
      <Link to="/" className="mt-2 text-sm font-medium text-emerald-700 hover:underline">
        Back to dashboard
      </Link>
    </div>
  )
}
