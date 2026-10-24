import { request } from '@/shared/api/client'
import type {
  AnalysisDefaults,
  Preset,
  PresetValues,
  SaveAnalysisDefaultsRequest,
  SecretStatus,
} from '@/shared/api/types'

export const settingsApi = {
  listSecrets(): Promise<SecretStatus[]> {
    return request('/api/secrets')
  },
  setSecret(name: string, value: string): Promise<SecretStatus> {
    return request(`/api/secrets/${encodeURIComponent(name)}`, {
      method: 'PUT',
      body: JSON.stringify({ value }),
    })
  },
  removeSecret(name: string): Promise<void> {
    return request(`/api/secrets/${encodeURIComponent(name)}`, { method: 'DELETE' })
  },
  listPresets({ allUsers = false }: { allUsers?: boolean } = {}): Promise<Preset[]> {
    return request(allUsers ? '/api/presets?allUsers=true' : '/api/presets')
  },
  createPreset(name: string, values: PresetValues): Promise<Preset> {
    return request('/api/presets', { method: 'POST', body: JSON.stringify({ name, values }) })
  },
  updatePreset(id: string, name: string, values: PresetValues, version?: number): Promise<Preset> {
    return request(`/api/presets/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify({ name, values, version }),
    })
  },
  deletePreset(id: string): Promise<void> {
    return request(`/api/presets/${encodeURIComponent(id)}`, { method: 'DELETE' })
  },
  getAnalysisDefaults(): Promise<AnalysisDefaults> {
    return request('/api/settings/analysis-defaults')
  },
  updateAnalysisDefaults(body: SaveAnalysisDefaultsRequest): Promise<AnalysisDefaults> {
    return request('/api/settings/analysis-defaults', {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  },
}
