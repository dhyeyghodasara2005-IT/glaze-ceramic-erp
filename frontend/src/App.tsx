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
import SilosPage from './features/production/SilosPage'
import PressPage from './features/production/PressPage'
import LotsPage from './features/production/LotsPage'
import LotPage from './features/production/LotPage'
import PressEntriesPage from './features/production/PressEntriesPage'
import PressEntryPage from './features/production/PressEntryPage'
import LinePage from './features/production/LinePage'
import LineEntriesPage from './features/production/LineEntriesPage'
import LineEntryPage from './features/production/LineEntryPage'
import FinishingPage from './features/production/FinishingPage'
import QualityChecksPage from './features/production/QualityChecksPage'
import PackEntriesPage from './features/production/PackEntriesPage'
import PackEntryPage from './features/production/PackEntryPage'
import FgStockPage from './features/fg/FgStockPage'
import FgGodownsPage from './features/fg/FgGodownsPage'
import GodownMapPage from './features/fg/GodownMapPage'
import FgDocumentsPage from './features/fg/FgDocumentsPage'
import FgDocumentPage from './features/fg/FgDocumentPage'
import SalesOrdersPage from './features/sales/SalesOrdersPage'
import SalesOrderPage from './features/sales/SalesOrderPage'
import LoadingPage from './features/sales/LoadingPage'
import LoadingListPage from './features/sales/LoadingListPage'
import PackingListPage from './features/sales/PackingListPage'
import BreakdownsPage from './features/maintenance/BreakdownsPage'
import DowntimePage from './features/maintenance/DowntimePage'

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
      {/* printouts: a page of their own, without the app's frame */}
      <Route
        path="/print/sales-orders/:id"
        element={
          <RequireAuth>
            <RequirePermission permission="SALES_VIEW"><PackingListPage /></RequirePermission>
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
        <Route path="parties/principals" element={<RequirePermission permission="PARTY_VIEW"><PartiesPage key="JOBWORK" type="JOBWORK" /></RequirePermission>} />
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
        <Route path="production/silos" element={<RequirePermission permission="PRODUCTION_VIEW"><SilosPage /></RequirePermission>} />
        <Route path="production/press" element={<RequirePermission permission="PRODUCTION_VIEW"><PressPage /></RequirePermission>} />
        <Route path="production/lots" element={<RequirePermission permission="PRODUCTION_VIEW"><LotsPage /></RequirePermission>} />
        <Route path="production/lots/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><LotPage /></RequirePermission>} />
        <Route path="production/press-entries" element={<RequirePermission permission="PRODUCTION_VIEW"><PressEntriesPage /></RequirePermission>} />
        <Route path="production/press-entries/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><PressEntryPage /></RequirePermission>} />
        <Route path="production/line" element={<RequirePermission permission="PRODUCTION_VIEW"><LinePage /></RequirePermission>} />
        {/* one list + one editor for every line stage; key makes React start fresh when the stage changes */}
        <Route path="production/glaze-entries" element={<RequirePermission permission="PRODUCTION_VIEW"><LineEntriesPage key="GLAZE" stage="GLAZE" /></RequirePermission>} />
        <Route path="production/glaze-entries/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><LineEntryPage key="GLAZE" stage="GLAZE" /></RequirePermission>} />
        <Route path="production/kiln-entries" element={<RequirePermission permission="PRODUCTION_VIEW"><LineEntriesPage key="KILN" stage="KILN" /></RequirePermission>} />
        <Route path="production/kiln-entries/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><LineEntryPage key="KILN" stage="KILN" /></RequirePermission>} />
        <Route path="production/finishing" element={<RequirePermission permission="PRODUCTION_VIEW"><FinishingPage /></RequirePermission>} />
        <Route path="production/quality-checks" element={<RequirePermission permission="PRODUCTION_VIEW"><QualityChecksPage /></RequirePermission>} />
        <Route path="production/polish-entries" element={<RequirePermission permission="PRODUCTION_VIEW"><LineEntriesPage key="POLISH" stage="POLISH" /></RequirePermission>} />
        <Route path="production/polish-entries/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><LineEntryPage key="POLISH" stage="POLISH" /></RequirePermission>} />
        <Route path="production/pack-entries" element={<RequirePermission permission="PRODUCTION_VIEW"><PackEntriesPage /></RequirePermission>} />
        <Route path="production/pack-entries/:id" element={<RequirePermission permission="PRODUCTION_VIEW"><PackEntryPage /></RequirePermission>} />
        <Route path="fg/stock" element={<RequirePermission permission="FG_VIEW"><FgStockPage /></RequirePermission>} />
        <Route path="fg/godowns" element={<RequirePermission permission="FG_VIEW"><FgGodownsPage /></RequirePermission>} />
        <Route path="fg/godowns/:id" element={<RequirePermission permission="FG_VIEW"><GodownMapPage /></RequirePermission>} />
        {/* one list + one editor for the three warehouse documents */}
        <Route path="fg/opening" element={<RequirePermission permission="FG_VIEW"><FgDocumentsPage key="OPENING" type="OPENING" /></RequirePermission>} />
        <Route path="fg/opening/:id" element={<RequirePermission permission="FG_VIEW"><FgDocumentPage key="OPENING" type="OPENING" /></RequirePermission>} />
        <Route path="fg/breakage" element={<RequirePermission permission="FG_VIEW"><FgDocumentsPage key="BREAKAGE" type="BREAKAGE" /></RequirePermission>} />
        <Route path="fg/breakage/:id" element={<RequirePermission permission="FG_VIEW"><FgDocumentPage key="BREAKAGE" type="BREAKAGE" /></RequirePermission>} />
        <Route path="fg/regrading" element={<RequirePermission permission="FG_VIEW"><FgDocumentsPage key="REGRADE" type="REGRADE" /></RequirePermission>} />
        <Route path="fg/regrading/:id" element={<RequirePermission permission="FG_VIEW"><FgDocumentPage key="REGRADE" type="REGRADE" /></RequirePermission>} />
        <Route path="sales/orders" element={<RequirePermission permission="SALES_VIEW"><SalesOrdersPage /></RequirePermission>} />
        <Route path="sales/orders/:id" element={<RequirePermission permission="SALES_VIEW"><SalesOrderPage /></RequirePermission>} />
        <Route path="sales/orders/:id/loading" element={<RequirePermission permission="SALES_VIEW"><LoadingPage /></RequirePermission>} />
        <Route path="sales/loading" element={<RequirePermission permission="SALES_VIEW"><LoadingListPage /></RequirePermission>} />
        <Route path="maintenance/breakdowns" element={<RequirePermission permission="MAINTENANCE_VIEW"><BreakdownsPage /></RequirePermission>} />
        <Route path="maintenance/downtime" element={<RequirePermission permission="MAINTENANCE_VIEW"><DowntimePage /></RequirePermission>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
