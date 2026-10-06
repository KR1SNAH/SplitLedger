import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { SelectField, TextareaField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { TextField } from '@/components/ui/TextField'
import { useCategories } from '@/features/categories/api'
import { ApiError } from '@/lib/api'
import { toIsoDate } from '@/lib/format'
import { applyServerErrors } from '@/lib/formErrors'
import type { Expense } from '@/lib/types'
import { useSaveExpense } from './api'

// Mirrors the backend's ExpenseRequest constraints. The amount stays a string until submit so
// what the user typed is validated exactly (no floating-point surprises).
const schema = z.object({
  description: z.string().trim().min(1, 'Enter a description').max(120, 'Keep it under 120 characters'),
  amount: z
    .string()
    .trim()
    .regex(/^\d{1,10}(\.\d{1,2})?$/, 'Enter an amount like 12.50')
    .refine((value) => Number(value) > 0, 'Amount must be more than 0'),
  expenseDate: z.iso.date('Pick a date'),
  categoryId: z.string(),
  notes: z.string().max(500, 'Keep notes under 500 characters'),
})

type FormValues = z.infer<typeof schema>

interface ExpenseFormDialogProps {
  open: boolean
  onClose: () => void
  /** Present when editing; absent when creating. */
  expense?: Expense | null
}

export function ExpenseFormDialog({ open, onClose, expense }: ExpenseFormDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title={expense ? 'Edit expense' : 'Add expense'}>
      {/* Keyed so the form resets whenever a different expense is opened. */}
      <ExpenseForm key={expense?.id ?? 'new'} expense={expense ?? null} onDone={onClose} />
    </Modal>
  )
}

function ExpenseForm({ expense, onDone }: { expense: Expense | null; onDone: () => void }) {
  const { data: categories = [] } = useCategories()
  const saveExpense = useSaveExpense()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      description: expense?.description ?? '',
      amount: expense ? expense.amount.toFixed(2) : '',
      expenseDate: expense?.expenseDate ?? toIsoDate(new Date()),
      categoryId: expense?.category?.id ?? '',
      notes: expense?.notes ?? '',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await saveExpense.mutateAsync({
        id: expense?.id,
        input: {
          description: values.description,
          amount: Number(values.amount),
          expenseDate: values.expenseDate,
          categoryId: values.categoryId || null,
          notes: values.notes.trim() || null,
          version: expense?.version,
        },
      })
      onDone()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setFormError('This expense was changed somewhere else. Close this dialog to load the latest version.')
      } else {
        setFormError(applyServerErrors(error, setError))
      }
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {formError && <Alert>{formError}</Alert>}
      <TextField label="Description" autoFocus error={errors.description?.message} {...register('description')} />
      <div className="grid grid-cols-2 gap-3">
        <TextField
          label="Amount"
          inputMode="decimal"
          placeholder="0.00"
          error={errors.amount?.message}
          {...register('amount')}
        />
        <TextField label="Date" type="date" error={errors.expenseDate?.message} {...register('expenseDate')} />
      </div>
      <SelectField label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
        <option value="">Uncategorized</option>
        {categories.map((category) => (
          <option key={category.id} value={category.id}>
            {category.name}
          </option>
        ))}
      </SelectField>
      <TextareaField label="Notes (optional)" error={errors.notes?.message} {...register('notes')} />
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
