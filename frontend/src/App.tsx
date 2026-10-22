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
import DesignsPage from './features/masters/designs/DesignsPage'
import FinishesPage from './features/masters/finishes/FinishesPage'
import CategoriesPage from './features/masters/categories/CategoriesPage'
import GradesPage from './features/masters/grades/GradesPage'
import MaterialsPage from './features/masters/materials/MaterialsPage'
import MachinesPage from './features/masters/machines/MachinesPage'
import MaterialGroupsPage from './features/masters/material-groups/MaterialGroupsPage'
import UnitsPage from './features/masters/units/UnitsPage'
import PartiesPage from './features/parties/PartiesPage'

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
        <Route path="masters/designs" element={<RequirePermission permission="MASTER_VIEW"><DesignsPage /></RequirePermission>} />
        <Route path="masters/sizes" element={<RequirePermission permission="MASTER_VIEW"><SizesPage /></RequirePermission>} />
        <Route path="masters/finishes" element={<RequirePermission permission="MASTER_VIEW"><FinishesPage /></RequirePermission>} />
        <Route path="masters/categories" element={<RequirePermission permission="MASTER_VIEW"><CategoriesPage /></RequirePermission>} />
        <Route path="masters/grades" element={<RequirePermission permission="MASTER_VIEW"><GradesPage /></RequirePermission>} />
        <Route path="masters/materials" element={<RequirePermission permission="MASTER_VIEW"><MaterialsPage /></RequirePermission>} />
        <Route path="masters/machines" element={<RequirePermission permission="MASTER_VIEW"><MachinesPage /></RequirePermission>} />
        <Route path="masters/material-groups" element={<RequirePermission permission="MASTER_VIEW"><MaterialGroupsPage /></RequirePermission>} />
        <Route path="masters/units" element={<RequirePermission permission="MASTER_VIEW"><UnitsPage /></RequirePermission>} />
        {/* one component, three routes; key makes React start fresh when switching type */}
        <Route path="parties/dealers" element={<RequirePermission permission="PARTY_VIEW"><PartiesPage key="DEALER" type="DEALER" /></RequirePermission>} />
        <Route path="parties/suppliers" element={<RequirePermission permission="PARTY_VIEW"><PartiesPage key="SUPPLIER" type="SUPPLIER" /></RequirePermission>} />
        <Route path="parties/transporters" element={<RequirePermission permission="PARTY_VIEW"><PartiesPage key="TRANSPORTER" type="TRANSPORTER" /></RequirePermission>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
