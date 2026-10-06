import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '@/auth/useAuth'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { SelectField } from '@/components/ui/fields'
import { TextField } from '@/components/ui/TextField'
import { CategoryManager } from '@/features/categories/CategoryManager'
import { api } from '@/lib/api'
import { CURRENCIES } from '@/lib/format'
import { applyServerErrors } from '@/lib/formErrors'
import type { User } from '@/lib/types'

const schema = z.object({
  displayName: z.string().trim().min(1, 'Enter your name').max(50, 'Keep it under 50 characters'),
  currency: z.string().length(3),
})

type FormValues = z.infer<typeof schema>

export function SettingsPage() {
  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
      <ProfileSection />
      <CategoryManager />
    </div>
  )
}

function ProfileSection() {
  const { user, updateUser } = useAuth()
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { displayName: user?.displayName ?? '', currency: user?.currency ?? 'USD' },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    setSaved(false)
    try {
      const updated = await api<User>('/api/users/me', { method: 'PUT', json: values })
      updateUser(updated)
      reset({ displayName: updated.displayName, currency: updated.currency })
      setSaved(true)
    } catch (error) {
      setFormError(applyServerErrors(error, setError))
    }
  })

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="font-semibold">Profile</h2>
      <p className="mt-1 text-sm text-slate-600">{user?.email}</p>
      <form onSubmit={onSubmit} noValidate className="mt-5 space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Name" error={errors.displayName?.message} {...register('displayName')} />
          <SelectField label="Currency" error={errors.currency?.message} {...register('currency')}>
            {CURRENCIES.map((c) => (
              <option key={c.code} value={c.code}>
                {c.code} — {c.label}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="flex items-center justify-end gap-3">
          {saved && !isDirty && <span className="text-sm text-emerald-700">Saved</span>}
          <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
            Save profile
          </Button>
        </div>
      </form>
    </section>
  )
}
