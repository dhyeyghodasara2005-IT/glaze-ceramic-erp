import { Route, Routes } from 'react-router-dom'
import { RequireAuth, RequirePermission } from './auth/guards'
import LoginPage from './auth/LoginPage'
import ChangePasswordPage from './auth/ChangePasswordPage'
import AppLayout from './layout/AppLayout'
import DashboardPage from './pages/DashboardPage'
import NotFoundPage from './pages/NotFoundPage'
import CompaniesPage from './features/companies/CompaniesPage'
import PlantsPage from './features/plants/PlantsPage'
import RolesPage from './features/roles/RolesPage'
import UsersPage from './features/users/UsersPage'
import SizesPage from './features/masters/sizes/SizesPage'

/**
 * Every page (URL) in the app. Pages inside <AppLayout> get the sidebar and top bar.
 * To add a module: add its routes here and its menu entry in layout/menu.tsx.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/change-password"
        element={
          <RequireAuth>
            <ChangePasswordPage />
          </RequireAuth>
        }
      />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="companies" element={<RequirePermission permission="COMPANY_VIEW"><CompaniesPage /></RequirePermission>} />
        <Route path="plants" element={<RequirePermission permission="PLANT_VIEW"><PlantsPage /></RequirePermission>} />
        <Route path="roles" element={<RequirePermission permission="ROLE_VIEW"><RolesPage /></RequirePermission>} />
        <Route path="users" element={<RequirePermission permission="USER_VIEW"><UsersPage /></RequirePermission>} />
        <Route path="masters/sizes" element={<RequirePermission permission="MASTER_VIEW"><SizesPage /></RequirePermission>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
