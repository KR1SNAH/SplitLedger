import { createBrowserRouter, RouterProvider } from 'react-router'
import { FullPageSpinner, GuestOnly, RequireAuth } from '@/auth/guards'
import { AppLayout } from '@/components/AppLayout'

// Every page is its own chunk, loaded the first time its route is visited. The shell (layout,
// auth guards, API client) stays in the main bundle.
const router = createBrowserRouter([
  {
    // Shown while the first page's chunk loads.
    hydrateFallbackElement: <FullPageSpinner />,
    children: [
      {
        element: <GuestOnly />,
        children: [
          { path: '/login', lazy: async () => ({ Component: (await import('@/pages/LoginPage')).LoginPage }) },
          { path: '/register', lazy: async () => ({ Component: (await import('@/pages/RegisterPage')).RegisterPage }) },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { index: true, lazy: async () => ({ Component: (await import('@/pages/DashboardPage')).DashboardPage }) },
              { path: '/expenses', lazy: async () => ({ Component: (await import('@/pages/ExpensesPage')).ExpensesPage }) },
              { path: '/budgets', lazy: async () => ({ Component: (await import('@/pages/BudgetsPage')).BudgetsPage }) },
              { path: '/groups', lazy: async () => ({ Component: (await import('@/pages/GroupsPage')).GroupsPage }) },
              {
                path: '/groups/:groupId',
                lazy: async () => ({ Component: (await import('@/pages/GroupDetailPage')).GroupDetailPage }),
              },
              {
                path: '/join/:token',
                lazy: async () => ({ Component: (await import('@/pages/JoinGroupPage')).JoinGroupPage }),
              },
              { path: '/settings', lazy: async () => ({ Component: (await import('@/pages/SettingsPage')).SettingsPage }) },
              {
                element: <RequireAuth role="ADMIN" />,
                children: [
                  { path: '/admin', lazy: async () => ({ Component: (await import('@/pages/AdminPage')).AdminPage }) },
                ],
              },
            ],
          },
        ],
      },
      { path: '*', lazy: async () => ({ Component: (await import('@/pages/NotFoundPage')).NotFoundPage }) },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
