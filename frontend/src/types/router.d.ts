import 'vue-router'

import type { PlatformRole } from '@/types/platform'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    requiresAuth?: boolean
    roles?: PlatformRole[]
  }
}
