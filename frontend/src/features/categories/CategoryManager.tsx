import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { CategoryBadge } from '@/components/CategoryBadge'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { TextField } from '@/components/ui/TextField'
import { applyServerErrors } from '@/lib/formErrors'
import type { Category } from '@/lib/types'
import { useCategories, useDeleteCategory, useSaveCategory } from './api'

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(50, 'Keep it under 50 characters'),
  color: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Pick a color'),
})

type FormValues = z.infer<typeof schema>

export function CategoryManager() {
  const { data: categories = [], isPending } = useCategories()
  const deleteCategory = useDeleteCategory()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [deleting, setDeleting] = useState<Category | null>(null)

  const custom = categories.filter((c) => !c.system)
  const builtIn = categories.filter((c) => c.system)

  function openForm(category: Category | null) {
    setEditing(category)
    setFormOpen(true)
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteCategory.mutateAsync(deleting.id)
      setDeleting(null)
    } catch {
      // Shown in the dialog via deleteCategory.error.
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="font-semibold">Categories</h2>
          <p className="mt-1 text-sm text-slate-600">Built-in categories are available to everyone. Add your own below.</p>
        </div>
        <Button variant="secondary" onClick={() => openForm(null)}>
          + New category
        </Button>
      </div>

      <h3 className="mt-6 text-sm font-medium text-slate-700">Your categories</h3>
      {isPending ? (
        <p className="mt-2 text-sm text-slate-500">Loading…</p>
      ) : custom.length === 0 ? (
        <p className="mt-2 text-sm text-slate-500">You haven't created any categories yet.</p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100">
          {custom.map((category) => (
            <li key={category.id} className="flex items-center justify-between py-2">
              <CategoryBadge category={category} />
              <div>
                <Button variant="ghost" className="px-2 py-1" onClick={() => openForm(category)}>
                  Edit
                </Button>
                <Button variant="ghost" className="px-2 py-1 text-red-600" onClick={() => setDeleting(category)}>
                  Delete
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <h3 className="mt-6 text-sm font-medium text-slate-700">Built-in</h3>
      <div className="mt-2 flex flex-wrap gap-2">
        {builtIn.map((category) => (
          <CategoryBadge key={category.id} category={category} />
        ))}
      </div>

      <Modal open={formOpen} onClose={() => setFormOpen(false)} title={editing ? 'Edit category' : 'New category'}>
        <CategoryForm key={editing?.id ?? 'new'} category={editing} onDone={() => setFormOpen(false)} />
      </Modal>

      <ConfirmDialog
        open={deleting !== null}
        title="Delete category?"
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onClose={() => {
          setDeleting(null)
          deleteCategory.reset()
        }}
        pending={deleteCategory.isPending}
        error={deleteCategory.error?.message}
      >
        <strong className="text-slate-900">{deleting?.name}</strong> will be deleted. Expenses in this category will
        become uncategorized.
      </ConfirmDialog>
    </section>
  )
}

function CategoryForm({ category, onDone }: { category: Category | null; onDone: () => void }) {
  const saveCategory = useSaveCategory()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: category?.name ?? '', color: category?.color ?? '#10b981' },
  })

  const onSubmit = handleSubmit(async (input) => {
    setFormError(null)
    try {
      await saveCategory.mutateAsync({ id: category?.id, input })
      onDone()
    } catch (error) {
      setFormError(applyServerErrors(error, setError))
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {formError && <Alert>{formError}</Alert>}
      <TextField label="Name" autoFocus error={errors.name?.message} {...register('name')} />
      <TextField
        label="Color"
        type="color"
        className="h-10 cursor-pointer p-1"
        error={errors.color?.message}
        {...register('color')}
      />
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {category ? 'Save changes' : 'Create category'}
        </Button>
      </div>
    </form>
  )
}
