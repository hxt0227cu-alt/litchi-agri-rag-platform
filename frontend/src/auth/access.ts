export type PlatformRole = 'farmer' | 'technician' | 'shopkeeper'
export type PlatformPermission =
  | 'diagnosis.access'
  | 'evaluation.access'
  | 'documents.manage'
  | 'training.access'
  | 'guide.access'
  | 'feedback.access'
  | 'system.manage'

const permissionMatrix: Record<PlatformRole, PlatformPermission[]> = {
  farmer: ['diagnosis.access', 'feedback.access'],
  technician: [
    'diagnosis.access',
    'evaluation.access',
    'documents.manage',
    'training.access',
    'guide.access',
    'feedback.access',
    'system.manage'
  ],
  shopkeeper: ['documents.manage', 'guide.access', 'feedback.access']
}

export const isPlatformRole = (role: string | null | undefined): role is PlatformRole =>
  role === 'farmer' || role === 'technician' || role === 'shopkeeper'

export const hasPermission = (
  role: string | null | undefined,
  permission: PlatformPermission
): boolean => {
  if (!isPlatformRole(role)) {
    return false
  }
  return permissionMatrix[role].includes(permission)
}

export const isRoleAllowed = (
  role: string | null | undefined,
  allowedRoles?: PlatformRole[]
): boolean => {
  if (!allowedRoles?.length) {
    return true
  }
  return isPlatformRole(role) && allowedRoles.includes(role)
}

export const defaultRouteForRole = (_role: string | null | undefined) => '/overview'

export const blockedRouteMessage = (path: string): string => {
  switch (path) {
    case '/evaluation':
      return '当前账号仅技术员可进入评测中心。'
    case '/diagnosis':
      return '当前账号仅农户和技术员可使用病害识别。'
    case '/training':
      return '当前账号仅技术员可进入培训课堂。'
    case '/guide':
      return '当前账号仅农资店和技术员可访问快配药与用药指南。'
    case '/feedback':
      return '请先登录后再填写满意度问卷。'
    case '/system':
      return '当前账号仅技术员可访问系统设置与运维页。'
    default:
      return '当前账号没有访问该页面的权限。'
  }
}
