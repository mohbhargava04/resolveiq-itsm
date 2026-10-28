import { useAuth } from '@/lib/auth'
import EmployeeHome from './EmployeeHome'
import TechnicianQueue from './TechnicianQueue'
import CommandCentre from './CommandCentre'

/** Each role lands on its own workspace. */
export default function Home() {
  const { user, is } = useAuth()
  if (!user || user.role === 'EMPLOYEE') return <EmployeeHome />
  if (is('TEAM_LEAD')) return <CommandCentre />
  return <TechnicianQueue />
}
