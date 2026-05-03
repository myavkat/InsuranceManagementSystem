import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import OverviewView from '../views/OverviewView.vue'
import AnalyticsView from '../views/AnalyticsView.vue'
import ReportsView from '../views/ReportsView.vue'
import PoliciesView from '../views/PoliciesView.vue'
import CreatePolicyView from '../views/CreatePolicyView.vue'
import RenewalsView from '../views/RenewalsView.vue'
import ClientsView from '../views/ClientsView.vue'
import AddClientView from '../views/AddClientView.vue'
import ClientDetailView from '../views/ClientDetailView.vue'
import ClaimsView from '../views/ClaimsView.vue'
import NewClaimView from '../views/NewClaimView.vue'
import UsersView from '../views/UsersView.vue'
import SettingsView from '../views/SettingsView.vue'
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
      path: '/dashboard/overview',
      name: 'overview',
      component: OverviewView,
    },
    {
      path: '/dashboard/analytics',
      name: 'analytics',
      component: AnalyticsView,
    },
    {
      path: '/dashboard/reports',
      name: 'reports',
      component: ReportsView,
    },
    {
      path: '/policies',
      name: 'policies',
      component: PoliciesView,
    },
    {
      path: '/policies/create',
      name: 'create-policy',
      component: CreatePolicyView,
    },
    {
      path: '/policies/renewals',
      name: 'renewals',
      component: RenewalsView,
    },
    {
      path: '/clients',
      name: 'clients',
      component: ClientsView,
    },
    {
      path: '/clients/add',
      name: 'add-client',
      component: AddClientView,
    },
    {
      path: '/clients/:id',
      name: 'client-detail',
      component: ClientDetailView,
    },
    {
      path: '/claims',
      name: 'claims',
      component: ClaimsView,
    },
    {
      path: '/claims/new',
      name: 'new-claim',
      component: NewClaimView,
    },
    {
      path: '/users',
      name: 'users',
      component: UsersView,
    },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsView,
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView,
    },
  ],
})

router.beforeEach(async (to, _from) => {
  const { isLoggedIn } = useAuth()

  const requiresAuth = to.meta.requiresAuth !== false
  const loggedIn = await isLoggedIn()

  if (requiresAuth && !loggedIn) {
    return '/login'
  } else if (to.path === '/login' && loggedIn) {
    return '/dashboard'
  } else {
    return true
  }
})

export default router