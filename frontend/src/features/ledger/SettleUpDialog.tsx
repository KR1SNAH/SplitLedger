import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '@/auth/useAuth'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { SelectField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { TextField } from '@/components/ui/TextField'
import { toIsoDate } from '@/lib/format'
import { applyServerErrors } from '@/lib/formErrors'
import type { GroupDetail } from '@/lib/types'
import { useRecordSettlement } from './api'

// Mirrors the backend's SettlementRequest.
const schema = z
  .object({
    fromUserId: z.string().min(1, 'Choose who paid'),
    toUserId: z.string().min(1, 'Choose who was paid'),
    amount: z
      .string()
      .trim()
      .regex(/^\d{1,10}(\.\d{1,2})?$/, 'Enter an amount like 12.50')
      .refine((value) => Number(value) > 0, 'Amount must be more than 0'),
    settledOn: z.iso.date('Pick a date'),
    note: z.string().max(200, 'Keep the note under 200 characters'),
  })
  .refine((values) => values.fromUserId !== values.toUserId, {
    path: ['toUserId'],
    message: 'Choose two different people',
  })

type FormValues = z.infer<typeof schema>

/** Pre-filled values, e.g. from a suggested payment. */
export interface SettleUpDraft {
  fromUserId: string
  toUserId: string
  amount?: number
}

interface SettleUpDialogProps {
  open: boolean
  onClose: () => void
  group: GroupDetail
  draft: SettleUpDraft | null
}

export function SettleUpDialog({ open, onClose, group, draft }: SettleUpDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title="Record a payment">
      <SettleUpForm
        key={draft ? `${draft.fromUserId}-${draft.toUserId}-${draft.amount}` : 'blank'}
        group={group}
        draft={draft}
        onDone={onClose}
      />
    </Modal>
  )
}

function SettleUpForm({ group, draft, onDone }: { group: GroupDetail; draft: SettleUpDraft | null; onDone: () => void }) {
  const { user } = useAuth()
  const recordSettlement = useRecordSettlement(group.id)
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fromUserId: draft?.fromUserId ?? user?.id ?? '',
      toUserId: draft?.toUserId ?? '',
      amount: draft?.amount !== undefined ? draft.amount.toFixed(2) : '',
      settledOn: toIsoDate(new Date()),
      note: '',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await recordSettlement.mutateAsync({
        fromUserId: values.fromUserId,
        toUserId: values.toUserId,
        amount: Number(values.amount),
        settledOn: values.settledOn,
        note: values.note.trim() || null,
      })
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(error, setError))
    }
  })

  const nameOf = (userId: string, name: string) => (userId === user?.id ? `${name} (you)` : name)

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <p className="text-sm text-slate-600">
        Record money that changed hands outside SplitLedger, like cash or a bank transfer.
        {group.myRole !== 'OWNER' && ' You can record payments you made or received.'}
      </p>
      {formError && <Alert>{formError}</Alert>}
      <div className="grid grid-cols-2 gap-3">
        <SelectField label="From" error={errors.fromUserId?.message} {...register('fromUserId')}>
          {group.members.map((m) => (
            <option key={m.userId} value={m.userId}>
              {nameOf(m.userId, m.displayName)}
            </option>
          ))}
        </SelectField>
        <SelectField label="To" error={errors.toUserId?.message} {...register('toUserId')}>
          <option value="">Choose…</option>
          {group.members.map((m) => (
            <option key={m.userId} value={m.userId}>
              {nameOf(m.userId, m.displayName)}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <TextField
          label={`Amount (${group.currency})`}
          inputMode="decimal"
          placeholder="0.00"
          error={errors.amount?.message}
          {...register('amount')}
        />
        <TextField label="Date" type="date" error={errors.settledOn?.message} {...register('settledOn')} />
      </div>
      <TextField label="Note (optional)" placeholder="e.g. Bank transfer" error={errors.note?.message} {...register('note')} />
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          Record payment
        </Button>
      </div>
    </form>
  )
}
