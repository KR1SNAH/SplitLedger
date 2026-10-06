import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '@/auth/useAuth'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { SelectField, TextareaField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { TextField } from '@/components/ui/TextField'
import { CURRENCIES } from '@/lib/format'
import { applyServerErrors } from '@/lib/formErrors'
import type { GroupDetail } from '@/lib/types'
import { useSaveGroup } from './api'

// Mirrors the backend's GroupRequest constraints.
const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(80, 'Keep it under 80 characters'),
  description: z.string().max(300, 'Keep it under 300 characters'),
  currency: z.string().length(3),
})

type FormValues = z.infer<typeof schema>

interface GroupFormDialogProps {
  open: boolean
  onClose: () => void
  /** Present when editing; absent when creating. */
  group?: GroupDetail | null
  onSaved?: (group: GroupDetail) => void
}

export function GroupFormDialog({ open, onClose, group, onSaved }: GroupFormDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title={group ? 'Edit group' : 'New group'}>
      <GroupForm key={group?.id ?? 'new'} group={group ?? null} onDone={onClose} onSaved={onSaved} />
    </Modal>
  )
}

function GroupForm({
  group,
  onDone,
  onSaved,
}: {
  group: GroupDetail | null
  onDone: () => void
  onSaved?: (group: GroupDetail) => void
}) {
  const { user } = useAuth()
  const saveGroup = useSaveGroup()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: group?.name ?? '',
      description: group?.description ?? '',
      currency: group?.currency ?? user?.currency ?? 'USD',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const saved = await saveGroup.mutateAsync({
        id: group?.id,
        input: { name: values.name, description: values.description.trim() || null, currency: values.currency },
      })
      onDone()
      onSaved?.(saved)
    } catch (error) {
      setFormError(applyServerErrors(error, setError))
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {formError && <Alert>{formError}</Alert>}
      <TextField
        label="Name"
        autoFocus
        placeholder="e.g. Lisbon trip, Flat 4B"
        error={errors.name?.message}
        {...register('name')}
      />
      <TextareaField label="Description (optional)" error={errors.description?.message} {...register('description')} />
      <SelectField label="Currency" error={errors.currency?.message} {...register('currency')}>
        {CURRENCIES.map((c) => (
          <option key={c.code} value={c.code}>
            {c.code} — {c.label}
          </option>
        ))}
      </SelectField>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {group ? 'Save changes' : 'Create group'}
        </Button>
      </div>
    </form>
  )
}
