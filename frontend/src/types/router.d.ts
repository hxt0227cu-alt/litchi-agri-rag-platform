import 'vue-router'

import type { PlatformRole } from '@/auth/access'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    requiresAuth?: boolean
    roles?: PlatformRole[]
  }
}
