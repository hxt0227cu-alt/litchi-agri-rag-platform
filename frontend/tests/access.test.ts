import assert from 'node:assert/strict'

import { blockedRouteMessage, defaultRouteForRole, hasPermission, isRoleAllowed } from '../src/auth/access.js'
import {
  CONSULTATION_STATUS_OPTIONS,
  DEFAULT_ROUTE_BY_ROLE,
  DISEASE_TAG_OPTIONS,
  PAGE_META,
  REMEDY_INVENTORY_OPTIONS,
  ROLE_NAV_ITEMS
} from '../src/config/platform.js'
import type { PlatformRole } from '../src/types/platform.js'

assert.equal(hasPermission('farmer', 'diagnosis.access'), true)
assert.equal(hasPermission('farmer', 'training.access'), true)
assert.equal(hasPermission('farmer', 'evaluation.access'), false)
assert.equal(isRoleAllowed('farmer', ['farmer', 'technician']), true)

assert.equal(hasPermission('technician', 'diagnosis.access'), true)
assert.equal(hasPermission('technician', 'evaluation.access'), true)
assert.equal(hasPermission('technician', 'documents.manage'), true)
assert.equal(hasPermission('technician', 'training.access'), true)
assert.equal(hasPermission('technician', 'knowledge.access'), true)
assert.equal(hasPermission('technician', 'system.manage'), true)

assert.equal(hasPermission('shopkeeper', 'documents.manage'), false)
assert.equal(hasPermission('shopkeeper', 'diagnosis.access'), false)
assert.equal(hasPermission('shopkeeper', 'training.access'), false)
assert.equal(isRoleAllowed('shopkeeper', ['farmer', 'technician']), false)

assert.equal(defaultRouteForRole('farmer'), '/farmer/workbench')
assert.equal(defaultRouteForRole('shopkeeper'), '/shop/workbench')
assert.equal(defaultRouteForRole('technician'), '/technician/workbench')
assert.equal(defaultRouteForRole('guest'), '/login')

assert.equal(blockedRouteMessage('/document'), '当前页面仅管理员可以访问知识文档。')
assert.equal(blockedRouteMessage('/shop/profile'), '当前页面仅农资店可以访问门店协同模块。')
assert.equal(blockedRouteMessage('/solutions'), '当前页面仅农户可以发起方案选择和求助。')

assert.equal(hasPermission('guest', 'documents.manage'), false)
assert.equal(isRoleAllowed('guest', ['technician']), false)
assert.equal(isRoleAllowed(undefined, ['technician']), false)

const roles: PlatformRole[] = ['farmer', 'shopkeeper', 'technician']
for (const role of roles) {
  const navItems = ROLE_NAV_ITEMS[role]
  assert.ok(navItems.length > 0, `${role} should expose at least one nav entry`)
  assert.equal(navItems[0].path, DEFAULT_ROUTE_BY_ROLE[role], `${role} default route should match first nav item`)

  for (const item of navItems) {
    assert.ok(PAGE_META[item.path], `${item.path} should have page metadata for layout rendering`)
  }
}

assert.equal(PAGE_META['/farmer/workbench'].title, '农户工作台')
assert.equal(PAGE_META['/shop/inbox'].title, '待处理求助')
assert.equal(PAGE_META['/technician/workbench'].title, '管理员工作台')

assert.deepEqual(
  CONSULTATION_STATUS_OPTIONS.map(option => option.value),
  ['pending', 'contacted', 'completed']
)
assert.ok(REMEDY_INVENTORY_OPTIONS.includes('有现货'))
assert.ok(REMEDY_INVENTORY_OPTIONS.includes('预订可配'))
assert.ok(DISEASE_TAG_OPTIONS.includes('炭疽病'))
assert.ok(DISEASE_TAG_OPTIONS.includes('霜疫霉病'))

console.log('frontend access tests passed')
