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
import StockPage from './features/store/StockPage'
import StoreDocumentsPage from './features/store/StoreDocumentsPage'
import StoreDocumentPage from './features/store/StoreDocumentPage'
import GodownsPage from './features/store/GodownsPage'
import ShiftsPage from './features/masters/shifts/ShiftsPage'
import BodyRecipesPage from './features/masters/recipes/BodyRecipesPage'
import BodyPrepPage from './features/production/BodyPrepPage'
import BodyBatchesPage from './features/production/BodyBatchesPage'
import BodyBatchPage from './features/production/BodyBatchPage'
import SprayRunsPage from './features/production/SprayRunsPage'
import SprayRunPage from './features/production/SprayRunPage'

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
        <Route path="store/stock" element={<RequirePermission permission="STORE_VIEW"><StockPage /></RequirePermission>} />
        <Route path="store/godowns" element={<RequirePermission permission="STORE_VIEW"><GodownsPage /></RequirePermission>} />
        {/* list + document page for each type: /store/grn, /store/grn/new, /store/grn/12 */}
        <Route path="store/grn" element={<RequirePermission permission="STORE_VIEW"><StoreDocumentsPage key="GRN" type="GRN" /></RequirePermission>} />
        <Route path="store/grn/:id" element={<RequirePermission permission="STORE_VIEW"><StoreDocumentPage key="GRN" type="GRN" /></RequirePermission>} />
        <Route path="store/issues" element={<RequirePermission permission="STORE_VIEW"><StoreDocumentsPage key="ISSUE" type="ISSUE" /></RequirePermission>} />
        <Route path="store/issues/:id" element={<RequirePermission permission="STORE_VIEW"><StoreDocumentPage key="ISSUE" type="ISSUE" /></RequirePermission>} />
        <Route path="store/adjustments" element={<RequirePermission permission="STORE_VIEW"><StoreDocumentsPage key="ADJUSTMENT" type="ADJUSTMENT" /></RequirePermission>} />
        <Route path="store/adjustments/:id" element={<RequirePermission permission="STORE_VIEW"><StoreDocumentPage key="ADJUSTMENT" type="ADJUSTMENT" /></RequirePermission>} />
        <Route path="masters/shifts" element={<RequirePermission permission="MASTER_VIEW"><ShiftsPage /></RequirePermission>} />
        <Route path="masters/body-recipes" element={<RequirePermission permission="MASTER_VIEW"><BodyRecipesPage /></RequirePermission>} />
        <Route path="production/body" element={<RequirePermission permission="PRODUCTION_VIEW"><BodyPrepPage /></RequirePermission>} />
        <Route path="production/body-batches" element={<RequirePermission permission="PRODUCTION_VIEW"><BodyBatchesPage /></RequirePermission>} />
        <Route path="production/body-batches/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><BodyBatchPage /></RequirePermission>} />
        <Route path="production/spray-runs" element={<RequirePermission permission="PRODUCTION_VIEW"><SprayRunsPage /></RequirePermission>} />
        <Route path="production/spray-runs/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><SprayRunPage /></RequirePermission>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
