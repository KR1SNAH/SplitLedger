import type { SplitType } from '@/lib/types'

export const SPLIT_TYPES: { value: SplitType; short: string; description: string }[] = [
  { value: 'EQUAL', short: 'Equally', description: 'split equally' },
  { value: 'EXACT', short: 'Amounts', description: 'split by exact amounts' },
  { value: 'PERCENT', short: 'Percent', description: 'split by percentage' },
  { value: 'SHARES', short: 'Shares', description: 'split by shares' },
]

export function describeSplit(splitType: SplitType) {
  return SPLIT_TYPES.find((t) => t.value === splitType)?.description ?? ''
}
