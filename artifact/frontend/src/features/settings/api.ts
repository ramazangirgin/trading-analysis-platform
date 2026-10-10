import { request } from '@/shared/api/client'
import type { Preset, SecretStatus } from '@/shared/api/types'

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
  listPresets(): Promise<Preset[]> {
    return request('/api/presets')
  },
  createPreset(name: string, values: Record<string, unknown>): Promise<Preset> {
    return request('/api/presets', { method: 'POST', body: JSON.stringify({ name, values }) })
  },
  updatePreset(
    id: string,
    name: string,
    values: Record<string, unknown>,
    version?: number,
  ): Promise<Preset> {
    return request(`/api/presets/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify({ name, values, version }),
    })
  },
  deletePreset(id: string): Promise<void> {
    return request(`/api/presets/${encodeURIComponent(id)}`, { method: 'DELETE' })
  },
}
