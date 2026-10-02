import type { AnalysisStatus } from '@/shared/api/types'

/** Statuses of a run that is not finished yet: pages keep polling while one is shown. */
export const ACTIVE_STATUSES: readonly AnalysisStatus[] = ['QUEUED', 'RUNNING']
