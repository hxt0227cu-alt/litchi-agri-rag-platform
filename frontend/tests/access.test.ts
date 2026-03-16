import assert from 'node:assert/strict'

import { blockedRouteMessage, defaultRouteForRole, hasPermission, isRoleAllowed } from '../src/auth/access.js'

assert.equal(hasPermission('farmer', 'diagnosis.access'), true)
assert.equal(hasPermission('farmer', 'evaluation.access'), false)
assert.equal(hasPermission('farmer', 'feedback.access'), true)
assert.equal(isRoleAllowed('farmer', ['farmer', 'technician']), true)

assert.equal(hasPermission('technician', 'diagnosis.access'), true)
assert.equal(hasPermission('technician', 'evaluation.access'), true)
assert.equal(hasPermission('technician', 'documents.manage'), true)
assert.equal(hasPermission('technician', 'training.access'), true)
assert.equal(hasPermission('technician', 'guide.access'), true)
assert.equal(hasPermission('technician', 'system.manage'), true)

assert.equal(hasPermission('shopkeeper', 'documents.manage'), true)
assert.equal(hasPermission('shopkeeper', 'diagnosis.access'), false)
assert.equal(hasPermission('shopkeeper', 'guide.access'), true)
assert.equal(isRoleAllowed('shopkeeper', ['farmer', 'technician']), false)
assert.equal(defaultRouteForRole('shopkeeper'), '/overview')
assert.equal(blockedRouteMessage('/system'), '当前账号仅技术员可访问系统设置与运维页。')
assert.equal(blockedRouteMessage('/guide'), '当前账号仅农资店和技术员可访问快配药与用药指南。')

assert.equal(hasPermission('guest', 'documents.manage'), false)
assert.equal(isRoleAllowed('guest', ['technician']), false)
assert.equal(isRoleAllowed(undefined, ['technician']), false)

console.log('frontend access tests passed')
