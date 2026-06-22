import { createRouter, createWebHistory } from 'vue-router'
import { adminGuard } from './adminGuard'
import { useAuth } from '../stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../pages/HomePage.vue') },
    { path: '/airports/:code', name: 'airport', component: () => import('../pages/AirportBusesPage.vue'), props: true },
    { path: '/bus/:sourceId', name: 'bus', component: () => import('../pages/BusDetailPage.vue'), props: true },
    { path: '/login', name: 'login', component: () => import('../pages/LoginPage.vue') },
    { path: '/reset-password', name: 'reset', component: () => import('../pages/ResetPasswordPage.vue') },
    { path: '/me', name: 'me', component: () => import('../pages/MePage.vue') },
    {
      path: '/inbox', name: 'inbox',
      component: () => import('../pages/InboxPage.vue'),
      beforeEnter: (to) => {
        const auth = useAuth()
        return auth.isAuthed ? true : { name: 'login', query: { redirect: to.fullPath } }
      },
    },
    {
      path: '/tickets', name: 'tickets',
      component: () => import('../pages/TicketsPage.vue'),
      beforeEnter: (to) => {
        const auth = useAuth()
        return auth.isAuthed ? true : { name: 'login', query: { redirect: to.fullPath } }
      },
    },
    {
      path: '/admin',
      component: () => import('../components/admin/AdminLayout.vue'),
      beforeEnter: adminGuard,
      children: [
        { path: '', name: 'admin-overview', component: () => import('../pages/admin/AdminOverviewPage.vue') },
        { path: 'subscriptions', name: 'admin-subscriptions', component: () => import('../pages/admin/AdminSubscriptionsPage.vue') },
        { path: 'hotness', name: 'admin-hotness', component: () => import('../pages/admin/AdminHotnessPage.vue') },
        { path: 'buses', name: 'admin-buses', component: () => import('../pages/admin/AdminBusesPage.vue') },
        { path: 'audit', name: 'admin-audit', component: () => import('../pages/admin/AdminAuditPage.vue') },
        { path: 'corrections', name: 'admin-corrections', component: () => import('../pages/admin/AdminCorrectionsPage.vue') },
        { path: 'tickets', name: 'admin-tickets', component: () => import('../pages/admin/AdminTicketsPage.vue') },
      ],
    },
  ],
})
