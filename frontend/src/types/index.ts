import type { PlatformRole } from '@/types/platform'

export interface User {
  id: string
  name: string
  role: PlatformRole
}
