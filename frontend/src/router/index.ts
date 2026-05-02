import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import { useAuth } from '../composables/useAuth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { requiresAuth: false },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: DashboardView,
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView,
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  const { isLoggedIn } = useAuth()

  const requiresAuth = to.meta.requiresAuth !== false
  const loggedIn = await isLoggedIn()

  if (requiresAuth && !loggedIn) {
    next('/login')
  } else if (to.path === '/login' && loggedIn) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
