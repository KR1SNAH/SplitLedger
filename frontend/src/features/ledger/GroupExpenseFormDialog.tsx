import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { useAuth } from '@/auth/useAuth'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { SelectField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { TextField } from '@/components/ui/TextField'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/cn'
import { formatMoney, toIsoDate } from '@/lib/format'
import { applyServerErrors } from '@/lib/formErrors'
import type { GroupDetail, GroupExpense, SplitPreviewInput, SplitType } from '@/lib/types'
import { useSaveGroupExpense, useSplitPreview } from './api'
import { SPLIT_TYPES } from './splitTypes'

// Same format the backend accepts: up to 10 digits and 2 decimals. Values stay strings until submit.
const DECIMAL = /^\d{1,10}(\.\d{1,2})?$/
const isPositiveDecimal = (value: string) => DECIMAL.test(value.trim()) && Number(value) > 0

// Mirrors the backend's GroupExpenseRequest. Whether the split adds up (percentages to 100, exact
// amounts to the total) is left to the server, which reports it through the live preview.
const schema = z
  .object({
    description: z.string().trim().min(1, 'Enter a description').max(120, 'Keep it under 120 characters'),
    amount: z.string().trim().refine(isPositiveDecimal, 'Enter an amount like 12.50'),
    expenseDate: z.iso.date('Pick a date'),
    paidBy: z.string().min(1, 'Choose who paid'),
    splitType: z.enum(['EQUAL', 'EXACT', 'PERCENT', 'SHARES']),
    participants: z.array(z.object({ userId: z.string(), name: z.string(), included: z.boolean(), value: z.string() })),
  })
  .superRefine((values, ctx) => {
    if (!values.participants.some((p) => p.included)) {
      ctx.addIssue({ code: 'custom', path: ['participants'], message: 'Choose at least one person to split with' })
    }
    if (values.splitType === 'EQUAL') return
    values.participants.forEach((p, i) => {
      if (p.included && !isPositiveDecimal(p.value)) {
        ctx.addIssue({ code: 'custom', path: ['participants', i, 'value'], message: 'Enter a number above 0' })
      }
    })
  })

type FormValues = z.infer<typeof schema>

/** The request body for the current form state, or null if it isn't complete enough to preview. */
function toSplitInput(values: Partial<FormValues>): SplitPreviewInput | null {
  const { amount = '', splitType = 'EQUAL', participants = [] } = values
  const included = participants.filter((p) => p.included)
  if (!isPositiveDecimal(amount) || included.length === 0) return null
  if (splitType !== 'EQUAL' && !included.every((p) => isPositiveDecimal(p.value))) return null
  return {
    amount: Number(amount),
    splitType,
    participants: included.map((p) => ({ userId: p.userId, value: splitType === 'EQUAL' ? null : Number(p.value) })),
  }
}

/** Sensible starting values when switching split type: 1 share each, otherwise blank. */
const defaultValueFor = (splitType: SplitType) => (splitType === 'SHARES' ? '1' : '')

interface GroupExpenseFormDialogProps {
  open: boolean
  onClose: () => void
  group: GroupDetail
  /** Present when editing; absent when adding. */
  expense?: GroupExpense | null
}

export function GroupExpenseFormDialog({ open, onClose, group, expense }: GroupExpenseFormDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title={expense ? 'Edit expense' : 'Add expense'}>
      {/* Keyed so the form resets whenever a different expense is opened. */}
      <GroupExpenseForm key={expense?.id ?? 'new'} group={group} expense={expense ?? null} onDone={onClose} />
    </Modal>
  )
}

function GroupExpenseForm({
  group,
  expense,
  onDone,
}: {
  group: GroupDetail
  expense: GroupExpense | null
  onDone: () => void
}) {
  const { user } = useAuth()
  const saveExpense = useSaveGroupExpense(group.id)
  const [formError, setFormError] = useState<string | null>(null)

  // One row per member. When editing, people who have since left the group keep their row.
  const shares = new Map(expense?.shares.map((s) => [s.userId, s]))
  const rows = [
    ...group.members.map((m) => ({ userId: m.userId, name: m.displayName })),
    ...(expense?.shares ?? [])
      .filter((s) => !group.members.some((m) => m.userId === s.userId))
      .map((s) => ({ userId: s.userId, name: `${s.displayName} (left)` })),
  ]
  const initialType = expense?.splitType ?? 'EQUAL'

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      description: expense?.description ?? '',
      amount: expense ? expense.amount.toFixed(2) : '',
      expenseDate: expense?.expenseDate ?? toIsoDate(new Date()),
      paidBy: expense?.paidBy.userId ?? user?.id ?? '',
      splitType: initialType,
      participants: rows.map((row) => {
        const share = shares.get(row.userId)
        return {
          ...row,
          included: expense ? share !== undefined : true,
          value: share?.value != null ? String(share.value) : defaultValueFor(initialType),
        }
      }),
    },
  })

  const values = useWatch({ control }) as FormValues
  const splitType = values.splitType
  // Stringified so the debounce only restarts when the split actually changes.
  const previewJson = useDebouncedValue(JSON.stringify(toSplitInput(values)), 300)
  const preview = useSplitPreview(group.id, JSON.parse(previewJson) as SplitPreviewInput | null)
  const owedByUser = new Map(preview.data?.map((s) => [s.userId, s.owed]))
  const previewIsCurrent = previewJson === JSON.stringify(toSplitInput(values))

  function setAllIncluded(included: boolean) {
    rows.forEach((_, i) => setValue(`participants.${i}.included`, included, { shouldValidate: !!errors.participants }))
  }

  const onSubmit = handleSubmit(async (form) => {
    setFormError(null)
    const split = toSplitInput(form)!
    try {
      await saveExpense.mutateAsync({
        id: expense?.id,
        input: {
          ...split,
          description: form.description,
          expenseDate: form.expenseDate,
          paidBy: form.paidBy,
          version: expense?.version,
        },
      })
      onDone()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setFormError(
          error.message.includes('changed elsewhere')
            ? 'This expense was changed somewhere else. Close this dialog to load the latest version.'
            : error.message,
        )
      } else {
        setFormError(applyServerErrors(error, setError))
      }
    }
  })

  const suffix = splitType === 'EXACT' ? group.currency : splitType === 'PERCENT' ? '%' : 'shares'

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {formError && <Alert>{formError}</Alert>}
      <TextField label="Description" autoFocus error={errors.description?.message} {...register('description')} />
      <div className="grid grid-cols-2 gap-3">
        <TextField
          label={`Amount (${group.currency})`}
          inputMode="decimal"
          placeholder="0.00"
          error={errors.amount?.message}
          {...register('amount')}
        />
        <TextField label="Date" type="date" error={errors.expenseDate?.message} {...register('expenseDate')} />
      </div>
      <SelectField label="Paid by" error={errors.paidBy?.message} {...register('paidBy')}>
        {rows.map((row) => (
          <option key={row.userId} value={row.userId}>
            {row.userId === user?.id ? `${row.name} (you)` : row.name}
          </option>
        ))}
      </SelectField>

      <fieldset>
        <legend className="text-sm font-medium text-slate-700">Split</legend>
        <div className="mt-1.5 grid grid-cols-4 gap-1 rounded-lg bg-slate-100 p-1">
          {SPLIT_TYPES.map((type) => (
            <label
              key={type.value}
              className={cn(
                'cursor-pointer rounded-md px-2 py-1.5 text-center text-xs font-medium text-slate-600 transition-colors',
                'has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-emerald-600',
                splitType === type.value ? 'bg-white text-slate-900 shadow-xs' : 'hover:text-slate-900',
              )}
            >
              <input
                type="radio"
                value={type.value}
                className="sr-only"
                {...register('splitType', {
                  onChange: (e) => {
                    const next = e.target.value as SplitType
                    rows.forEach((_, i) => setValue(`participants.${i}.value`, defaultValueFor(next)))
                  },
                })}
              />
              {type.short}
            </label>
          ))}
        </div>
      </fieldset>

      <div>
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-slate-700">Split between</p>
          <div className="flex gap-1 text-xs">
            <button type="button" className="text-emerald-700 hover:underline" onClick={() => setAllIncluded(true)}>
              All
            </button>
            <span className="text-slate-300">·</span>
            <button type="button" className="text-emerald-700 hover:underline" onClick={() => setAllIncluded(false)}>
              None
            </button>
          </div>
        </div>
        <ul className="mt-1.5 max-h-64 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
          {rows.map((row, i) => {
            const participant = values.participants?.[i]
            const valueError = errors.participants?.[i]?.value?.message
            const owed = participant?.included && previewIsCurrent ? owedByUser.get(row.userId) : undefined
            return (
              <li key={row.userId} className="flex items-center gap-3 px-3 py-2">
                <label className="flex min-w-0 flex-1 items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 rounded accent-emerald-600"
                    {...register(`participants.${i}.included`)}
                  />
                  <span className="truncate">{row.userId === user?.id ? `${row.name} (you)` : row.name}</span>
                </label>
                {splitType !== 'EQUAL' && participant?.included && (
                  <div className="flex items-center gap-1.5">
                    <input
                      aria-label={`${row.name}: ${suffix}`}
                      inputMode="decimal"
                      aria-invalid={valueError ? true : undefined}
                      title={valueError}
                      className={cn(
                        'w-20 rounded-md border px-2 py-1 text-right text-sm outline-none focus:ring-2',
                        valueError
                          ? 'border-red-400 focus:ring-red-100'
                          : 'border-slate-300 focus:border-emerald-500 focus:ring-emerald-100',
                      )}
                      {...register(`participants.${i}.value`)}
                    />
                    <span className="w-10 text-xs text-slate-500">{suffix}</span>
                  </div>
                )}
                <span className="w-20 text-right text-sm tabular-nums text-slate-700">
                  {owed !== undefined ? formatMoney(owed, group.currency) : ''}
                </span>
              </li>
            )
          })}
        </ul>
        {errors.participants?.message && <p className="mt-1.5 text-sm text-red-600">{errors.participants.message}</p>}
        {rows.some((_, i) => errors.participants?.[i]?.value) && (
          <p className="mt-1.5 text-sm text-red-600">Enter a number above 0 for everyone in the split.</p>
        )}
        {previewIsCurrent && preview.error && <p className="mt-1.5 text-sm text-amber-700">{preview.error.message}</p>}
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {expense ? 'Save changes' : 'Add expense'}
        </Button>
      </div>
    </form>
  )
}
