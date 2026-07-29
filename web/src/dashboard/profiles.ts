// Dashboard profiles live server-side (one JSON file per profile) via the /dashboards API. Only the
// *active* selection is per-device, kept in localStorage. The server stores the config opaquely.
import { api } from '../api/client'
import type { DashboardConfig } from './dashboardTypes'
import { DEFAULT_PRESET, PRESETS } from './presets'

const ACTIVE_KEY = 'ergpower.activeProfile'
const LEGACY_KEY = 'ergpower.dashboard' // pre-profiles single local dashboard, migrated on first load

export async function listProfiles(): Promise<string[]> {
  const { data } = await api.GET('/dashboards')
  return (data ?? []).map((d) => d.name)
}

export async function getProfile(name: string): Promise<DashboardConfig | null> {
  const { data } = await api.GET('/dashboards/{name}', { params: { path: { name } } })
  return data ? (data.config as unknown as DashboardConfig) : null
}

export async function putProfile(name: string, config: DashboardConfig): Promise<void> {
  await api.PUT('/dashboards/{name}', {
    params: { path: { name } },
    body: { ...config, name } as unknown as Record<string, unknown>,
  })
}

export async function deleteProfile(name: string): Promise<void> {
  await api.DELETE('/dashboards/{name}', { params: { path: { name } } })
}

export function getActiveName(): string | null {
  return localStorage.getItem(ACTIVE_KEY)
}

export function setActiveName(name: string): void {
  localStorage.setItem(ACTIVE_KEY, name)
}

export interface Bootstrap {
  names: string[]
  active: string
  config: DashboardConfig
}

/** Ensure ≥1 server profile exists (migrating a legacy local layout, else seeding a preset), then
 *  resolve the active profile and its config. */
export async function bootstrapProfiles(): Promise<Bootstrap> {
  let names = await listProfiles()
  if (names.length === 0) {
    const seed = migrateLegacyOrSeed()
    const name = seed.name?.trim() || 'Default'
    await putProfile(name, { ...seed, name })
    names = [name]
    setActiveName(name)
  }
  let active = getActiveName()
  if (!active || !names.includes(active)) {
    active = names[0]
    setActiveName(active)
  }
  const config = (await getProfile(active)) ?? PRESETS[DEFAULT_PRESET]()
  return { names, active, config }
}

function migrateLegacyOrSeed(): DashboardConfig {
  const legacy = localStorage.getItem(LEGACY_KEY)
  if (legacy) {
    try {
      return JSON.parse(legacy) as DashboardConfig
    } catch {
      // corrupt legacy value — fall through to a preset
    }
  }
  return PRESETS[DEFAULT_PRESET]()
}
