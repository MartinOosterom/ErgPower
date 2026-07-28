import createClient from 'openapi-fetch'
import type { paths } from './schema'

// REST client typed by the generated paths. baseUrl matches the spec server (/api/v1); in dev the
// Vite proxy forwards /api → the running `serve`.
export const api = createClient<paths>({ baseUrl: '/api/v1' })

// SSE lives outside openapi-fetch (native EventSource); its URL shares the same base.
export const LIVE_STREAM_URL = '/api/v1/live/stream'
