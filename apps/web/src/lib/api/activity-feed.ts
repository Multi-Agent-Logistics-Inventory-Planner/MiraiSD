import { apiGet } from './client'
import type { ActivityFeedEvent } from '@/types/dashboard'

const BASE_PATH = '/api/activity-feed'

export interface ActivityFeedParams {
  limit?: number
  types?: string[]
  includeResolved?: boolean
}

export async function getActivityFeed(params: ActivityFeedParams = {}): Promise<ActivityFeedEvent[]> {
  const searchParams = new URLSearchParams()

  if (params.limit) {
    searchParams.set('limit', params.limit.toString())
  }

  if (params.types?.length) {
    params.types.forEach(t => searchParams.append('types', t))
  }

  if (params.includeResolved) {
    searchParams.set('includeResolved', 'true')
  }

  const query = searchParams.toString()
  return apiGet<ActivityFeedEvent[]>(`${BASE_PATH}${query ? `?${query}` : ''}`)
}
