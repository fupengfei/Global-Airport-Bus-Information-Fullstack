import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { useAuth } from '../stores/auth'

export const isAdminRole = (role?: string) => role === 'SUPER_ADMIN' || role === 'OPERATOR'

/** /admin 守卫:未登录→登录(带回跳);已登录非管理员→首页;管理员→放行。
 *  后端仍独立 403 兜底,前端守卫只是体验。 */
export async function adminGuard(
  to: RouteLocationNormalized,
): Promise<boolean | RouteLocationRaw> {
  const auth = useAuth()
  if (!auth.isAuthed) return { name: 'login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try { await auth.loadMe() } catch { return { name: 'login', query: { redirect: to.fullPath } } }
  }
  if (!isAdminRole(auth.user?.role)) return { name: 'home' }
  return true
}
