import { DEFAULT_ROUTE_BY_ROLE } from '../config/platform.js'
import type { PlatformRole } from '../types/platform.js'

export type PlatformPermission =
  | 'diagnosis.access'
  | 'evaluation.access'
  | 'documents.manage'
  | 'training.access'
  | 'feedback.access'
  | 'knowledge.access'
  | 'system.manage'

const permissionMatrix: Record<PlatformRole, PlatformPermission[]> = {
  farmer: ['diagnosis.access', 'training.access', 'feedback.access', 'knowledge.access'],
  technician: [
    'diagnosis.access',
    'evaluation.access',
    'documents.manage',
    'training.access',
    'feedback.access',
    'knowledge.access',
    'system.manage'
  ],
  shopkeeper: []
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

export const defaultRouteForRole = (role: string | null | undefined) => {
  if (!isPlatformRole(role)) {
    return '/login'
  }
  return DEFAULT_ROUTE_BY_ROLE[role]
}

export const blockedRouteMessage = (path: string): string => {
  switch (path) {
    case '/evaluation':
      return '当前页面仅管理员可以访问评测中心。'
    case '/diagnosis':
      return '当前页面仅农户和管理员可以访问病害识别。'
    case '/training':
      return '当前页面仅农户和管理员可以访问学习课堂。'
    case '/knowledge':
      return '当前页面仅农户和管理员可以访问研判图谱。'
    case '/document':
      return '当前页面仅管理员可以访问知识文档。'
    case '/system':
    case '/technician/workbench':
      return '当前页面仅管理员可以访问管理员工作台与系统状态。'
    case '/shop/workbench':
    case '/shop/profile':
    case '/shop/plans':
    case '/shop/inbox':
    case '/shop/trends':
      return '当前页面仅农资店可以访问门店协同模块。'
    case '/consultations/my':
    case '/solutions':
      return '当前页面仅农户可以发起方案选择和求助。'
    case '/feedback':
      return '请先登录后再提交满意度反馈。'
    default:
      return '当前账号没有访问该页面的权限。'
  }
}
