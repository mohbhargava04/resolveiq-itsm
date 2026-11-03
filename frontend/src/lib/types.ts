// Types mirroring the backend DTOs (backend/src/main/java/com/resolveiq/web/dto).

export type Role = 'EMPLOYEE' | 'TECHNICIAN' | 'TEAM_LEAD' | 'ADMIN' | 'MANAGER'
export type Priority = 'P1' | 'P2' | 'P3' | 'P4'
export type Level = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type IncidentStatus =
  | 'NEW' | 'CLASSIFIED' | 'ASSIGNED' | 'ACKNOWLEDGED' | 'IN_PROGRESS'
  | 'AWAITING_INFO' | 'RESOLVED' | 'VERIFIED' | 'CLOSED'
export type SlaStatus = 'NOT_STARTED' | 'ON_TRACK' | 'AT_RISK' | 'PAUSED' | 'BREACHED' | 'MET'
export type EscalationLevel = 'NONE' | 'TEAM_LEAD' | 'ADMINISTRATOR' | 'IT_MANAGER'
export type IncidentAction =
  | 'EDIT' | 'ASSIGN' | 'ACKNOWLEDGE' | 'START_WORK' | 'REQUEST_INFO' | 'PROVIDE_INFO' | 'RESOLVE'
  | 'VERIFY' | 'REOPEN' | 'CLOSE' | 'ADD_NOTE' | 'ADD_INTERNAL_NOTE' | 'REPRIORITISE' | 'ESCALATE'
  | 'MARK_DUPLICATE' | 'FEEDBACK' | 'DECLARE_MAJOR' | 'LINK_TO_MAJOR' | 'POST_MAJOR_UPDATE' | 'PUBLISH_ARTICLE'

export interface Ref { code: string; name: string }
export interface PersonRef { id: number; name: string; role: Role }

export interface TechnicianInfo {
  skills: string[]
  experienceLevel: string
  capacity: number
  available: boolean
  activeIncidents?: number
  teamLeadId?: number
  teamLeadName?: string
}

export interface User {
  id: number
  fullName: string
  email: string
  role: Role
  roleLabel: string
  initials: string
  phone?: string
  department?: string
  location?: string
  avatarUrl?: string
  active: boolean
  lastLoginAt?: string
  profileCompleted: boolean
  passwordEnabled: boolean
  googleLinked: boolean
  mfaEnabled: boolean
  lockedUntil?: string
  technician?: TechnicianInfo
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: User
  mfaRequired?: boolean
  challenge?: string
  mfaSetupRequired?: boolean
}

export interface DemoAccount { role: Role; label: string; email: string; lands: string }
export interface AuthOptions {
  providers: { id: string; name: string }[]
  selfRegistration: boolean
  allowedDomains: string[]
  demoMode: boolean
  demoPassword?: string
  demoAccounts?: DemoAccount[]
}

export interface SessionView {
  id: string; device: string; ipAddress?: string; method: string; signedInAt: string
  lastActiveAt: string; expiresAt: string; current: boolean
}

export interface SecurityOverview {
  activeSessions: number; lockedAccounts: number; privilegedAccounts: number; privilegedWithMfa: number
  failedLogins24h: number; lockouts24h: number; ssoRejected30d: number; tokenReuse30d: number
  passwordResets30d: number; googleSignInEnabled: boolean; allowedDomains: string[]; mfaRequiredForPrivileged: boolean
}

export interface IncidentSummary {
  number: string
  title: string
  status: IncidentStatus
  priority?: Priority
  priorityLabel?: string
  category?: Ref
  service: Ref
  reporter: PersonRef
  assignee?: PersonRef
  createdAt: string
  updatedAt: string
  slaStatus: SlaStatus
  responseDueAt?: string
  resolutionDueAt?: string
  minutesRemaining?: number
  escalationLevel: EscalationLevel
  suspectedDuplicateOf?: string
  autoClassified: boolean
  major: boolean
  majorParent?: string
}

export interface SlaView {
  policyName?: string
  responseMinutes?: number
  resolutionMinutes?: number
  startedAt?: string
  responseDueAt?: string
  resolutionDueAt?: string
  respondedAt?: string
  resolvedAt?: string
  responseBreached: boolean
  resolutionBreached: boolean
  paused: boolean
  status: SlaStatus
  elapsedPercent: number
  minutesRemaining?: number
  responseMinutesTaken?: number
  resolutionMinutesTaken?: number
}

export interface NoteView {
  id: number
  type: 'INVESTIGATION' | 'COMMENT' | 'INFO_REQUEST' | 'INFO_RESPONSE' | 'RESOLUTION' | 'SYSTEM'
  internal: boolean
  author: string
  content: string
  createdAt: string
}

export interface IncidentDetail {
  number: string
  title: string
  description: string
  status: IncidentStatus
  statusLabel: string
  priority?: Priority
  priorityLabel?: string
  priorityScore?: number
  priorityRationale?: string
  priorityStrategy?: string
  category?: Ref
  autoClassified: boolean
  classificationConfidence?: number
  service: Ref
  serviceCriticality: Level
  impact: Level
  urgency: Level
  affectedUsers: number
  reporter: PersonRef
  assignee?: PersonRef
  assignedAt?: string
  assignmentStrategy?: string
  sla: SlaView
  escalation: { level: EscalationLevel; label: string; escalatedAt?: string; escalatedTo?: PersonRef }
  resolution?: { summary: string; rootCause?: string; resolvedBy?: PersonRef; resolvedAt?: string }
  feedback?: { rating: number; comment?: string; submittedAt: string }
  duplicateOf?: { number: string; title: string; score?: number; confirmed: boolean }
  createdAt: string
  updatedAt: string
  closedAt?: string
  closureNote?: string
  notes: NoteView[]
  availableActions: IncidentAction[]
  nextStatuses: IncidentStatus[]
  major?: MajorView
  majorParent?: { number: string; title: string; stage?: MajorStage; stageLabel?: string; latestUpdate?: MajorUpdate }
  knowledgeArticleId?: number
  attachmentCount: number
}

export type MajorStage = 'INVESTIGATING' | 'IDENTIFIED' | 'MONITORING' | 'RESOLVED'
export interface MajorUpdate { stage: MajorStage; stageLabel: string; message: string; author: string; at: string }
export interface MajorView {
  stage: MajorStage; stageLabel: string; declaredAt: string; updates: MajorUpdate[]
  linked: { number: string; title: string; status: IncidentStatus; reporter: PersonRef }[]
}
export interface MajorBanner {
  number: string; title: string; service: Ref; priority?: Priority; stage: MajorStage; stageLabel: string
  declaredAt: string; latestUpdate?: MajorUpdate; linkedCount: number; affectsMe: boolean
}

export interface AttachmentView {
  id: number; fileName: string; contentType: string; sizeBytes: number; image: boolean
  uploadedBy: string; uploadedAt: string; canDelete: boolean
}

export interface ArticleSummary {
  id: number; title: string; excerpt: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; audience: 'EVERYONE' | 'IT_STAFF'
  category?: Ref; service?: Ref; views: number; helpfulVotes: number; deflections: number; relevance?: number; updatedAt: string
}
export interface ArticleView {
  id: number; title: string; symptoms: string; solution: string; status: ArticleSummary['status']; audience: ArticleSummary['audience']
  category?: Ref; service?: Ref; tags: string[]; sourceIncident?: string; author: string; views: number
  helpfulVotes: number; unhelpfulVotes: number; deflections: number; myVote?: boolean; publishedAt?: string
  updatedAt: string; related: ArticleSummary[]
}

export interface BulkResult { number: string; ok: boolean; message: string }

export interface SimilarIncident {
  number: string
  title: string
  status: IncidentStatus
  service: string
  score: number
  textSimilarity: number
  sameService: boolean
}

export interface CategorySuggestion {
  categoryCode?: string
  categoryName?: string
  confidence: number
  alternatives: { categoryCode: string; categoryName: string; probability: number }[]
  signals: string[]
  model: string
}

export interface PriorityAssessment { priority: Priority; score: number; strategy: string; factors: string[] }
export interface PriorityPreview { assessment: PriorityAssessment; slaPolicy: string; responseMinutes: number; resolutionMinutes: number }

export interface ReportResponse {
  incident: IncidentDetail
  similarIncidents: SimilarIncident[]
  categorySuggestion?: CategorySuggestion
  assignmentMessage?: string
}

export interface AuditEntry {
  id: number
  occurredAt: string
  actor: string
  actorRole?: string
  action: string
  incidentNumber?: string
  previousState?: string
  newState?: string
  description: string
}

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

export interface SlaRisk {
  level: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'BREACHED' | 'PAUSED'
  score: number
  minutesRemaining?: number
  expectedMinutesToResolve?: number
  factors: string[]
}

export interface SuggestedResolution {
  incidentNumber: string
  title: string
  serviceName: string
  categoryName?: string
  summary: string
  rootCause?: string
  relevance: number
  resolvedAt?: string
}

export interface Recommendation {
  technician: PersonRef
  score: number
  activeIncidents: number
  capacity: number
  skills: string[]
  reasons: string[]
}

export interface CorrelationCluster {
  majorNumber?: string
  suggestedMajor?: string
  rootServiceCode: string
  rootServiceName: string
  rootHasDirectReports: boolean
  incidentNumbers: string[]
  affectedServices: string[]
  confidence: number
  summary: string
  firstReportedAt: string
  lastReportedAt: string
}

export interface Overview {
  totalIncidents: number
  openIncidents: number
  criticalOpen: number
  resolvedIncidents: number
  closedIncidents: number
  slaBreaches: number
  slaCompliancePercent: number
  mttaMinutes?: number
  mttrMinutes?: number
  averageCsat?: number
  escalatedOpen: number
  unassignedOpen: number
  awaitingInfo: number
  autoClassifiedPercent: number
  duplicatesDetected: number
}

export interface CountRow { key: string; label: string; total: number; open: number; breached: number }
export interface DayRow { date: string; created: number; resolved: number; breached: number }
export interface SlaRow { priority: Priority; total: number; met: number; breached: number; compliancePercent: number; avgResponseMinutes?: number; avgResolutionMinutes?: number }
export interface TechnicianLoad {
  id: number; name: string; role: Role; available: boolean; activeIncidents: number; capacity: number
  utilisationPercent: number; resolvedLast30Days: number; avgResolutionMinutes?: number; csat?: number
  slaBreaches: number; skills: string[]
}

export interface QueueItem { incident: IncidentSummary; risk: SlaRisk }

export interface Dashboard {
  role: Role
  // employee
  open?: number
  needsMyAction?: IncidentSummary[]
  resolved?: number
  recent?: IncidentSummary[]
  // technician / team lead
  available?: boolean
  activeIncidents?: number
  capacity?: number
  toAcknowledge?: number
  myQueue?: QueueItem[]
  // management
  overview?: Overview
  unassigned?: IncidentSummary[]
  atRisk?: QueueItem[]
  correlations?: CorrelationCluster[]
  majorIncidents?: MajorBanner[]
  knowledge?: { deflections: number; views: number }
  byPriority?: CountRow[]
  recentActivity?: AuditEntry[]
}

export interface ServiceView {
  id: number; code: string; name: string; description?: string; criticality: Level
  ownerTeam?: string; userBase: number; active: boolean; dependsOn: string[]
}
export interface CategoryView { id: number; code: string; name: string; description?: string; keywords: string[]; securitySensitive: boolean; active: boolean }
export interface SlaPolicyView { id: number; name: string; priority: Priority; serviceCode?: string; serviceName: string; responseMinutes: number; resolutionMinutes: number; warningThresholdPercent: number; active: boolean }
export interface DependencyGraph {
  nodes: { code: string; name: string; criticality: Level; openIncidents: number }[]
  edges: { from: string; to: string }[]
}

export interface NotificationView {
  id: number; subject: string; message: string; incidentNumber?: string; eventType?: string
  urgent: boolean; read: boolean; createdAt: string
}

export interface StrategyView { key: string; name: string; description: string; active: boolean }
export interface Settings {
  PRIORITY_STRATEGY: string
  ASSIGNMENT_STRATEGY: string
  AUTO_ASSIGNMENT: string
  AUTO_ESCALATION: string
  AUTO_CLOSE_VERIFIED: string
  ALLOWED_EMAIL_DOMAINS: string
  SELF_REGISTRATION: string
  AUTO_PROVISION_SSO: string
  REQUIRE_MFA_FOR_PRIVILEGED: string
  priorityStrategies: StrategyView[]
  assignmentStrategies: StrategyView[]
  notificationChannels: string[]
}

export interface OutboxRow {
  id: number; channel: 'IN_APP' | 'EMAIL' | 'SMS'; recipient: string; address?: string; subject: string
  status: 'SENT' | 'SIMULATED' | 'FAILED'; incidentNumber?: string; urgent: boolean; createdAt: string; error?: string
  message?: string
}

export interface InboundEmail {
  id: number; receivedAt: string; from: string; subject: string; excerpt?: string; outcome: string
  incidentNumber?: string; detail?: string
}
