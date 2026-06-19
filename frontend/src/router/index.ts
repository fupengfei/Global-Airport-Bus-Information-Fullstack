import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../pages/HomePage.vue') },
    { path: '/airports/:code', name: 'airport', component: () => import('../pages/AirportBusesPage.vue'), props: true },
    { path: '/bus/:sourceId', name: 'bus', component: () => import('../pages/BusDetailPage.vue'), props: true },
    { path: '/login', name: 'login', component: () => import('../pages/LoginPage.vue') },
    { path: '/reset-password', name: 'reset', component: () => import('../pages/ResetPasswordPage.vue') },
    { path: '/me', name: 'me', component: () => import('../pages/MePage.vue') },
  ],
})
