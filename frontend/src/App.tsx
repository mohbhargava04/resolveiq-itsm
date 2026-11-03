import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { AppShell, RoleGate } from '@/components/shell'
import { Spinner } from '@/components/ui'
import Login from '@/pages/Login'
import Register from '@/pages/Register'
import ForgotPassword from '@/pages/ForgotPassword'
import ResetPassword from '@/pages/ResetPassword'
import Welcome from '@/pages/Welcome'

const Home = lazy(() => import('@/pages/Home'))
const ReportIncident = lazy(() => import('@/pages/ReportIncident'))
const Incidents = lazy(() => import('@/pages/Incidents'))
const IncidentDetail = lazy(() => import('@/pages/IncidentDetail'))
const TechnicianQueue = lazy(() => import('@/pages/TechnicianQueue'))
const ServiceMap = lazy(() => import('@/pages/ServiceMap'))
const Reports = lazy(() => import('@/pages/Reports'))
const AdminUsers = lazy(() => import('@/pages/admin/Users'))
const AdminCatalogue = lazy(() => import('@/pages/admin/Catalogue'))
const AdminSla = lazy(() => import('@/pages/admin/SlaPolicies'))
const AdminSettings = lazy(() => import('@/pages/admin/Settings'))
const AdminAudit = lazy(() => import('@/pages/admin/AuditLog'))
const AdminOutbox = lazy(() => import('@/pages/admin/Outbox'))
const AdminSecurity = lazy(() => import('@/pages/admin/Security'))
const Profile = lazy(() => import('@/pages/Profile'))
const KnowledgeBase = lazy(() => import('@/pages/KnowledgeBase'))
const KnowledgeArticle = lazy(() => import('@/pages/KnowledgeArticle'))
const KnowledgeEditor = lazy(() => import('@/pages/KnowledgeEditor'))

function FullPageSpinner() {
  return <div className="flex min-h-[50vh] items-center justify-center"><Spinner className="size-5" /></div>
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading, mfaSetupRequired } = useAuth()
  const location = useLocation()
  if (loading) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  // first company-account sign-in: confirm profile details before anything else
  if (!user.profileCompleted) return <Navigate to="/welcome" replace />
  // organisation policy: administrators must set up two-step verification first
  if (mfaSetupRequired && location.pathname !== '/profile') return <Navigate to="/profile?tab=security" replace />
  return <>{children}</>
}

function SignedInOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/welcome" element={<SignedInOnly><Welcome /></SignedInOnly>} />
      <Route element={<RequireAuth><AppShell /></RequireAuth>}>
        <Route path="/" element={<S><Home /></S>} />
        <Route path="/report" element={<S><ReportIncident /></S>} />
        <Route path="/incidents" element={<S><Incidents /></S>} />
        <Route path="/incidents/:number" element={<S><IncidentDetail /></S>} />
        <Route path="/profile" element={<S><Profile /></S>} />
        <Route path="/kb" element={<S><KnowledgeBase /></S>} />
        <Route path="/kb/new" element={<S><RoleGate minimum="TECHNICIAN"><KnowledgeEditor /></RoleGate></S>} />
        <Route path="/kb/:id" element={<S><KnowledgeArticle /></S>} />
        <Route path="/kb/:id/edit" element={<S><RoleGate minimum="TECHNICIAN"><KnowledgeEditor /></RoleGate></S>} />
        <Route path="/queue" element={<S><RoleGate minimum="TECHNICIAN"><TechnicianQueue /></RoleGate></S>} />
        <Route path="/services" element={<S><RoleGate minimum="TECHNICIAN"><ServiceMap /></RoleGate></S>} />
        <Route path="/reports" element={<S><RoleGate minimum="TEAM_LEAD"><Reports /></RoleGate></S>} />
        <Route path="/admin/users" element={<S><RoleGate minimum="ADMIN"><AdminUsers /></RoleGate></S>} />
        <Route path="/admin/catalogue" element={<S><RoleGate minimum="ADMIN"><AdminCatalogue /></RoleGate></S>} />
        <Route path="/admin/sla" element={<S><RoleGate minimum="ADMIN"><AdminSla /></RoleGate></S>} />
        <Route path="/admin/settings" element={<S><RoleGate minimum="ADMIN"><AdminSettings /></RoleGate></S>} />
        <Route path="/admin/audit" element={<S><RoleGate minimum="ADMIN"><AdminAudit /></RoleGate></S>} />
        <Route path="/admin/outbox" element={<S><RoleGate minimum="ADMIN"><AdminOutbox /></RoleGate></S>} />
        <Route path="/admin/security" element={<S><RoleGate minimum="ADMIN"><AdminSecurity /></RoleGate></S>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

function S({ children }: { children: ReactNode }) {
  return <Suspense fallback={<FullPageSpinner />}>{children}</Suspense>
}

function NotFound() {
  return (
    <div className="py-24 text-center">
      <p className="text-lg font-semibold">This page doesn't exist</p>
      <a href="/" className="mt-2 inline-block text-[13px] text-accent-text hover:underline">Go to home</a>
    </div>
  )
}
