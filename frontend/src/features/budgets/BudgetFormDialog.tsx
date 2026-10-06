import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { SelectField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { TextField } from '@/components/ui/TextField'
import { useCategories } from '@/features/categories/api'
import { applyServerErrors } from '@/lib/formErrors'
import type { BudgetStatus } from '@/lib/types'
import { useCreateBudget, useUpdateBudget } from './api'

/** Select value for the overall budget (the API uses a null category). */
const OVERALL = 'overall'

// Mirrors the backend's BudgetRequest constraints; the amount stays a string until submit.
const schema = z.object({
  categoryId: z.string().min(1, 'Pick what this budget is for'),
  amount: z
    .string()
    .trim()
    .regex(/^\d{1,10}(\.\d{1,2})?$/, 'Enter an amount like 250 or 99.50')
    .refine((value) => Number(value) > 0, 'Amount must be more than 0'),
})

type FormValues = z.infer<typeof schema>

interface BudgetFormDialogProps {
  open: boolean
  onClose: () => void
  /** Present when editing; absent when creating. */
  budget?: BudgetStatus | null
  /** The user's current budgets, so a category can't be picked twice. */
  existing: BudgetStatus[]
}

export function BudgetFormDialog({ open, onClose, budget, existing }: BudgetFormDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title={budget ? 'Edit budget' : 'New budget'}>
      <BudgetForm key={budget?.id ?? 'new'} budget={budget ?? null} existing={existing} onDone={onClose} />
    </Modal>
  )
}

function BudgetForm({
  budget,
  existing,
  onDone,
}: {
  budget: BudgetStatus | null
  existing: BudgetStatus[]
  onDone: () => void
}) {
  const { data: categories = [] } = useCategories()
  const createBudget = useCreateBudget()
  const updateBudget = useUpdateBudget()
  const [formError, setFormError] = useState<string | null>(null)

  const hasOverall = existing.some((b) => b.category === null)
  const budgeted = new Set(existing.map((b) => b.category?.id))
  const available = categories.filter((c) => !budgeted.has(c.id))
  const nothingLeft = !budget && hasOverall && available.length === 0

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      categoryId: budget ? (budget.category?.id ?? OVERALL) : hasOverall ? '' : OVERALL,
      amount: budget ? budget.amount.toFixed(2) : '',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const amount = Number(values.amount)
      if (budget) {
        await updateBudget.mutateAsync({ id: budget.id, amount })
      } else {
        await createBudget.mutateAsync({ categoryId: values.categoryId === OVERALL ? null : values.categoryId, amount })
      }
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(error, setError))
    }
  })

  if (nothingLeft) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          You already have an overall budget and one for every category. Edit an existing budget instead.
        </p>
        <div className="flex justify-end">
          <Button variant="secondary" onClick={onDone}>
            Close
          </Button>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {formError && <Alert>{formError}</Alert>}
      {budget ? (
        <p className="text-sm text-slate-600">
          Monthly limit for{' '}
          <strong className="text-slate-900">{budget.category?.name ?? 'all spending (overall)'}</strong>.
        </p>
      ) : (
        <SelectField label="Budget for" error={errors.categoryId?.message} {...register('categoryId')}>
          <option value="" disabled>
            Choose…
          </option>
          {!hasOverall && <option value={OVERALL}>Overall (all spending)</option>}
          {available.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </SelectField>
      )}
      <TextField
        label="Monthly limit"
        inputMode="decimal"
        placeholder="0.00"
        autoFocus={Boolean(budget)}
        error={errors.amount?.message}
        {...register('amount')}
      />
      <p className="text-xs text-slate-500">Applies to every month until you change it. Only personal expenses count.</p>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {budget ? 'Save changes' : 'Create budget'}
        </Button>
      </div>
    </form>
  )
}
