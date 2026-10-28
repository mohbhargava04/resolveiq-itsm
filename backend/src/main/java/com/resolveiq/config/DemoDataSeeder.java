package com.resolveiq.config;

import com.resolveiq.audit.AuditRepository;
import com.resolveiq.audit.AuditTrailObserver;
import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.catalog.Criticality;
import com.resolveiq.catalog.ItService;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.incident.IncidentFactory;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Classification;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.NoteType;
import com.resolveiq.incident.domain.Resolution;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.incident.event.IncidentEventPublisher;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.priority.PriorityAssessment;
import com.resolveiq.priority.PriorityEngine;
import com.resolveiq.sla.SlaPolicy;
import com.resolveiq.sla.SlaPolicyRepository;
import com.resolveiq.sla.SlaPolicyResolver;
import com.resolveiq.user.UserFactory;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.Role;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a realistic organisation for development and the demo video: 13 IT services with a
 * dependency graph, 9 categories, SLA policies, 14 users across all roles, ~36 historical incidents
 * (the knowledge base and analytics history) and a live scenario that exercises every intelligent
 * feature - a correlated outage, a duplicate, an unacknowledged P1 that will escalate, an SLA at risk,
 * an incident waiting for the reporter and one waiting for verification.
 *
 * <p>All incidents are created through the real domain methods, so their audit trails are genuine.</p>
 */
@Service
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "Password@123";

    private final UserRepository users;
    private final UserFactory userFactory;
    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final IncidentRepository incidents;
    private final IncidentFactory incidentFactory;
    private final PriorityEngine priorityEngine;
    private final SlaPolicyResolver slaResolver;
    private final AuditTrailObserver auditObserver;
    private final AuditRepository auditRepository;
    private final IncidentEventPublisher publisher;
    private final Clock clock;

    private final Map<String, ItService> svc = new HashMap<>();
    private final Map<String, Category> cat = new HashMap<>();
    private final Map<String, User> people = new LinkedHashMap<>();

    public DemoDataSeeder(UserRepository users, UserFactory userFactory, ItServiceRepository services,
                          CategoryRepository categories, SlaPolicyRepository slaPolicies, IncidentRepository incidents,
                          IncidentFactory incidentFactory, PriorityEngine priorityEngine, SlaPolicyResolver slaResolver,
                          AuditTrailObserver auditObserver, AuditRepository auditRepository,
                          IncidentEventPublisher publisher, Clock clock) {
        this.users = users;
        this.userFactory = userFactory;
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.incidents = incidents;
        this.incidentFactory = incidentFactory;
        this.priorityEngine = priorityEngine;
        this.slaResolver = slaResolver;
        this.auditObserver = auditObserver;
        this.auditRepository = auditRepository;
        this.publisher = publisher;
        this.clock = clock;
    }

    public boolean isEmpty() {
        return users.count() == 0;
    }

    @Transactional
    public void seed() {
        Instant now = clock.instant();
        seedCategories();
        seedServices();
        seedSlaPolicies();
        seedUsers();
        seedHistory(now);
        seedLiveScenario(now);
        log.info("Demo data ready: {} users, {} services, {} incidents. Every demo password is {}",
                users.count(), services.count(), incidents.count(), DEMO_PASSWORD);
    }

    // =========================================================================================
    // Reference data
    // =========================================================================================

    private void seedCategories() {
        category("HARDWARE", "Hardware", "Laptops, desktops, monitors, peripherals",
                "laptop, desktop, monitor, keyboard, mouse, battery, charger, power, screen, hardware, device, broken, not turning on", false);
        category("SOFTWARE", "Software", "Desktop applications, installation, licences, updates",
                "software, install, installation, licence, update, excel, word, office, outlook, teams, zoom, crash, driver", false);
        category("NETWORK", "Network", "Wi-Fi, LAN, internet, VPN, DNS",
                "network, wifi, internet, vpn, dns, connectivity, router, switch, lan, ethernet, slow internet, disconnect", false);
        category("DATABASE", "Database", "Database servers, queries, performance, data issues",
                "database, sql, query, deadlock, db, slow query, connection pool, replication, report totals, data", false);
        category("CLOUD", "Cloud", "Cloud hosting, virtual machines, containers",
                "cloud, aws, azure, vm, virtual machine, kubernetes, container, pod, deployment, server, instance", false);
        category("SECURITY", "Security", "Phishing, malware, suspicious activity, breaches",
                "phishing, malware, virus, ransomware, suspicious, hacked, breach, spam, attack, compromised, unauthorized", true);
        category("AUTHENTICATION", "Authentication & Access", "Login, SSO, passwords, MFA, account lockouts",
                "login, sso, password, reset, mfa, otp, locked, account, sign in, access denied, authentication", false);
        category("APPLICATION", "Business Application", "Portals and business applications (ERP, HR, payments)",
                "application, portal, website, page, error, button, form, submit, checkout, erp, hr portal, payment, 500, 503", false);
        category("STORAGE", "Storage", "File shares, cloud drives, quotas, backups, uploads",
                "storage, disk, drive, quota, space, upload, file, folder, backup, restore, shared drive, onedrive", false);
    }

    private void category(String code, String name, String description, String keywords, boolean securitySensitive) {
        Set<String> kw = Set.of(keywords.split("\\s*,\\s*"));
        cat.put(code, categories.save(new Category(code, name, description, kw, securitySensitive)));
    }

    private void seedServices() {
        service("NETWORK_CORE", "Core Network & DNS", "Campus backbone, firewalls, DNS and VPN", Criticality.CRITICAL, "Network Operations", 6000);
        service("CORE_DB", "Core Database Cluster", "Shared PostgreSQL cluster behind most business applications", Criticality.CRITICAL, "Data Platform", 6000, "NETWORK_CORE");
        service("IDENTITY", "Identity & SSO", "Single sign-on, MFA and directory services", Criticality.HIGH, "Identity & Access", 6000, "CORE_DB", "NETWORK_CORE");
        service("PAYMENT", "Payment Gateway", "Fee and purchase payments (UPI, cards, net banking)", Criticality.CRITICAL, "Finance Systems", 5000, "CORE_DB", "IDENTITY", "NETWORK_CORE");
        service("ERP_FINANCE", "ERP Finance", "Accounting, purchasing and payroll", Criticality.HIGH, "Finance Systems", 300, "CORE_DB", "IDENTITY");
        service("HR_PORTAL", "HR Portal", "Leave, attendance and employee records", Criticality.MEDIUM, "HR Technology", 1200, "CORE_DB", "IDENTITY");
        service("EMAIL", "Email & Collaboration", "Mail, calendars and Teams", Criticality.HIGH, "Digital Workplace", 6000, "IDENTITY", "NETWORK_CORE");
        service("CLOUD_PLATFORM", "Cloud Hosting Platform", "Kubernetes clusters and virtual machines", Criticality.HIGH, "Cloud Engineering", 800, "NETWORK_CORE");
        service("CLOUD_STORAGE", "Cloud Storage", "Shared drives and object storage", Criticality.HIGH, "Cloud Engineering", 6000, "CLOUD_PLATFORM");
        service("LMS", "Learning Portal", "Student portal, courses and lecture videos", Criticality.MEDIUM, "Academic Technology", 5000, "IDENTITY", "CLOUD_STORAGE", "CORE_DB");
        service("CAMPUS_WIFI", "Campus Wi-Fi", "Wireless network across campus and hostels", Criticality.MEDIUM, "Network Operations", 5000, "NETWORK_CORE");
        service("PRINTING", "Printing Services", "Managed printers and scanners", Criticality.LOW, "Digital Workplace", 1500, "NETWORK_CORE");
        service("DEVICES", "End-user Devices", "Laptops, desktops and peripherals", Criticality.MEDIUM, "Desktop Support", 6000);
    }

    private void service(String code, String name, String description, Criticality criticality, String owner,
                         int userBase, String... dependsOn) {
        ItService s = new ItService(code, name, description, criticality, owner, userBase);
        for (String d : dependsOn) {
            s.addDependency(svc.get(d));
        }
        svc.put(code, services.save(s));
    }

    private void seedSlaPolicies() {
        // Organisation-wide defaults (the table in the project brief)
        slaPolicies.save(new SlaPolicy("Default P1 – Critical", Priority.P1, null, 5, 30, 75));
        slaPolicies.save(new SlaPolicy("Default P2 – High", Priority.P2, null, 15, 120, 75));
        slaPolicies.save(new SlaPolicy("Default P3 – Medium", Priority.P3, null, 60, 480, 80));
        slaPolicies.save(new SlaPolicy("Default P4 – Low", Priority.P4, null, 240, 1440, 80));
        // Stricter, service-specific agreements
        slaPolicies.save(new SlaPolicy("Payment Gateway P1", Priority.P1, svc.get("PAYMENT"), 5, 20, 70));
        slaPolicies.save(new SlaPolicy("Payment Gateway P2", Priority.P2, svc.get("PAYMENT"), 10, 60, 70));
        slaPolicies.save(new SlaPolicy("Core Database P1", Priority.P1, svc.get("CORE_DB"), 5, 25, 70));
    }

    private void seedUsers() {
        person("ava", Role.EMPLOYEE, "Ava Sharma", "ava@resolveiq.test", "+91 98480 11001", "Computer Science", null, null);
        person("rohan", Role.EMPLOYEE, "Rohan Gupta", "rohan@resolveiq.test", "+91 98480 11002", "Finance", null, null);
        person("priya", Role.EMPLOYEE, "Priya Nair", "priya@resolveiq.test", "+91 98480 11003", "Human Resources", null, null);
        person("arjun", Role.EMPLOYEE, "Arjun Mehta", "arjun@resolveiq.test", "+91 98480 11004", "Accounts", null, null);
        person("sneha", Role.EMPLOYEE, "Sneha Iyer", "sneha@resolveiq.test", "+91 98480 11005", "Student Affairs", null, null);

        person("neha", Role.TEAM_LEAD, "Neha Joshi", "neha@resolveiq.test", "+91 98480 22001", "Service Desk",
                Set.of("NETWORK", "SECURITY", "AUTHENTICATION", "APPLICATION"), ExperienceLevel.EXPERT);
        person("vikram", Role.TEAM_LEAD, "Vikram Singh", "vikram@resolveiq.test", "+91 98480 22002", "Platform Engineering",
                Set.of("DATABASE", "CLOUD", "STORAGE"), ExperienceLevel.EXPERT);

        person("rahul", Role.TECHNICIAN, "Rahul Verma", "rahul@resolveiq.test", "+91 98480 33001", "Service Desk",
                Set.of("NETWORK", "SECURITY", "AUTHENTICATION"), ExperienceLevel.SENIOR);
        person("meera", Role.TECHNICIAN, "Meera Das", "meera@resolveiq.test", "+91 98480 33002", "Application Support",
                Set.of("APPLICATION", "DATABASE", "CLOUD"), ExperienceLevel.SENIOR);
        person("kabir", Role.TECHNICIAN, "Kabir Roy", "kabir@resolveiq.test", "+91 98480 33003", "Desktop Support",
                Set.of("HARDWARE", "SOFTWARE", "STORAGE"), ExperienceLevel.MID);
        person("isha", Role.TECHNICIAN, "Isha Kapoor", "isha@resolveiq.test", "+91 98480 33004", "Application Support",
                Set.of("APPLICATION", "SOFTWARE", "AUTHENTICATION"), ExperienceLevel.JUNIOR);
        person("dev", Role.TECHNICIAN, "Dev Malhotra", "dev@resolveiq.test", "+91 98480 33005", "Platform Engineering",
                Set.of("CLOUD", "STORAGE", "DATABASE", "NETWORK"), ExperienceLevel.MID);

        person("admin", Role.ADMIN, "Ananya Mehta", "admin@resolveiq.test", "+91 98480 44001", "IT Operations", null, null);
        person("manager", Role.MANAGER, "Sanjay Rao", "manager@resolveiq.test", "+91 98480 44002", "IT Leadership", null, null);

        TeamLead neha = (TeamLead) people.get("neha");
        TeamLead vikram = (TeamLead) people.get("vikram");
        tech("rahul").reportTo(neha);
        tech("isha").reportTo(neha);
        tech("kabir").reportTo(neha);
        tech("meera").reportTo(vikram);
        tech("dev").reportTo(vikram);
    }

    private void person(String key, Role role, String name, String email, String phone, String dept, Set<String> skills,
                        ExperienceLevel level) {
        User u = userFactory.create(new UserFactory.NewUser(role, name, email, DEMO_PASSWORD, phone, dept, skills, level,
                role == Role.TEAM_LEAD ? 3 : 5));
        people.put(key, users.save(u));
    }

    private Technician tech(String key) {
        return (Technician) people.get(key);
    }

    // =========================================================================================
    // Historical incidents (knowledge base, analytics, classifier training data)
    // =========================================================================================

    private record History(String title, String description, String service, String category, Impact impact,
                           Urgency urgency, int users, String resolution, String rootCause, int minutesToResolve,
                           int rating, String technician, String reporter) {
    }

    private void seedHistory(Instant now) {
        List<History> history = List.of(
                h("Payment gateway returning 502 errors", "Customers see a 502 Bad Gateway at checkout and payments are not going through.", "PAYMENT", "APPLICATION", Impact.CRITICAL, Urgency.CRITICAL, 800, "Restarted the payment API pods and rolled back release 4.2.1.", "Memory leak in release 4.2.1 exhausted the API pods", 26, 5, "meera", "arjun"),
                h("UPI transactions timing out", "UPI payments hang for 60 seconds and then fail with a timeout.", "PAYMENT", "NETWORK", Impact.HIGH, Urgency.CRITICAL, 300, "Increased the gateway connection pool and re-routed traffic to the secondary bank endpoint.", "Bank endpoint degraded; connection pool saturated", 95, 4, "rahul", "rohan"),
                h("Core database CPU at 100%", "Database server CPU is pegged and queries are extremely slow across applications.", "CORE_DB", "DATABASE", Impact.HIGH, Urgency.HIGH, 600, "Killed a runaway reporting query and added a missing index on transactions.created_at.", "Unindexed report query doing full table scans", 70, 4, "vikram", "rohan"),
                h("Database deadlocks on fee payment table", "Intermittent deadlock errors when many students pay fees at the same time.", "CORE_DB", "DATABASE", Impact.HIGH, Urgency.HIGH, 200, "Reordered lock acquisition in the payment batch job and shortened its transactions.", "Batch job and online payments locking rows in opposite order", 180, 4, "meera", "arjun"),
                h("Cannot log in with SSO", "The single sign-on page loops back to the login screen; no portal is accessible.", "IDENTITY", "AUTHENTICATION", Impact.HIGH, Urgency.HIGH, 400, "Renewed the expired SAML signing certificate on the identity provider.", "SAML certificate expired at midnight", 40, 5, "rahul", "sneha"),
                h("Password reset emails not arriving", "Users requesting a password reset never receive the email.", "IDENTITY", "AUTHENTICATION", Impact.MEDIUM, Urgency.MEDIUM, 25, "Fixed the SMTP relay credentials used by the identity service.", "Relay password rotated without updating the service", 210, 4, "isha", "priya"),
                h("Account locked after password change", "My account keeps getting locked after I changed my password.", "IDENTITY", "AUTHENTICATION", Impact.LOW, Urgency.MEDIUM, 1, "Removed the old saved password from the user's phone mail app and unlocked the account.", "Stale credentials on a mobile device", 35, 5, "isha", "ava"),
                h("Wi-Fi not working in hostel block C", "No wifi connectivity in hostel block C since the evening.", "CAMPUS_WIFI", "NETWORK", Impact.HIGH, Urgency.MEDIUM, 250, "Replaced the failed PoE switch powering the block C access points.", "PoE switch failure", 300, 3, "rahul", "sneha"),
                h("Slow internet in the library", "Internet is very slow in the library and pages take a long time to load.", "CAMPUS_WIFI", "NETWORK", Impact.MEDIUM, Urgency.MEDIUM, 80, "Moved library access points to 5 GHz channels and enabled band steering.", "Channel congestion on 2.4 GHz", 400, 4, "dev", "ava"),
                h("VPN connection drops frequently", "Remote VPN disconnects every few minutes when working from home.", "NETWORK_CORE", "NETWORK", Impact.MEDIUM, Urgency.MEDIUM, 15, "Updated the VPN client to the latest version and lowered the MTU to 1400.", "Packet fragmentation with an older client", 120, 4, "rahul", "rohan"),
                h("Outlook not receiving emails", "Outlook shows no new emails since morning and the inbox is not updating.", "EMAIL", "SOFTWARE", Impact.LOW, Urgency.HIGH, 1, "Rebuilt the corrupted Outlook profile and re-synced the mailbox.", "Corrupted local OST file", 50, 5, "kabir", "priya"),
                h("Emails stuck in outbox", "Messages sit in the outbox and are never sent.", "EMAIL", "APPLICATION", Impact.MEDIUM, Urgency.MEDIUM, 30, "Cleared the mail transport queue and restarted the SMTP connector.", "SMTP connector hung after a certificate update", 90, 4, "isha", "arjun"),
                h("Mailbox full, cannot receive mail", "Getting mailbox quota exceeded errors and new mail bounces.", "EMAIL", "STORAGE", Impact.LOW, Urgency.MEDIUM, 1, "Increased the mailbox quota to 50 GB and enabled online archiving.", "Mailbox quota reached", 25, 5, "kabir", "rohan"),
                h("Laptop not powering on", "My laptop does not turn on even when it is charging.", "DEVICES", "HARDWARE", Impact.LOW, Urgency.HIGH, 1, "Replaced the faulty charger and reseated the battery; the laptop boots normally.", "Faulty power adapter", 60, 5, "kabir", "ava"),
                h("External monitor flickering", "The external monitor flickers constantly when connected.", "DEVICES", "HARDWARE", Impact.LOW, Urgency.LOW, 1, "Replaced the damaged HDMI cable.", "Damaged cable", 45, 4, "kabir", "sneha"),
                h("Printer on 2nd floor jammed", "Printer shows a paper jam error but there is no paper inside.", "PRINTING", "HARDWARE", Impact.LOW, Urgency.MEDIUM, 20, "Cleaned the paper path sensor and replaced the worn pickup roller.", "Worn pickup roller", 70, 4, "kabir", "priya"),
                h("Cannot print from laptop", "Print jobs fail with a driver error.", "PRINTING", "SOFTWARE", Impact.LOW, Urgency.MEDIUM, 1, "Reinstalled the printer driver from the software centre.", "Driver corrupted after an OS update", 30, 5, "isha", "rohan"),
                h("Excel crashes when opening large files", "Excel crashes whenever I open the finance reports.", "DEVICES", "SOFTWARE", Impact.LOW, Urgency.MEDIUM, 3, "Installed the latest Office update and disabled a faulty add-in.", "Incompatible COM add-in", 55, 4, "isha", "arjun"),
                h("Statistics software installation blocked", "Cannot install the statistics software because admin rights are required.", "DEVICES", "SOFTWARE", Impact.LOW, Urgency.LOW, 1, "Published the package in the self-service software centre.", "Package not available for self-service", 240, 5, "kabir", "sneha"),
                h("Lecture video upload failing", "Uploading lecture videos to the learning portal fails at 99%.", "LMS", "STORAGE", Impact.MEDIUM, Urgency.HIGH, 40, "Raised the storage bucket upload limit and increased the proxy timeout.", "2 GB upload limit and 60 s proxy timeout", 110, 4, "dev", "sneha"),
                h("Department shared drive full", "The department shared drive says storage is full.", "CLOUD_STORAGE", "STORAGE", Impact.MEDIUM, Urgency.MEDIUM, 60, "Expanded the department quota and archived files older than three years.", "Quota reached", 150, 4, "dev", "priya"),
                h("Files missing from shared drive", "A whole folder disappeared from the shared drive.", "CLOUD_STORAGE", "STORAGE", Impact.MEDIUM, Urgency.HIGH, 10, "Restored the folder from the 24-hour snapshot.", "Accidental deletion by a user", 65, 5, "dev", "rohan"),
                h("Research VM unreachable over SSH", "The research virtual machine cannot be reached over SSH.", "CLOUD_PLATFORM", "CLOUD", Impact.MEDIUM, Urgency.HIGH, 5, "Rebooted the VM from the console and fixed the firewall rule blocking port 22.", "Firewall rule change", 85, 4, "dev", "sneha"),
                h("Student portal pods crash looping", "Student portal pods restart continuously and the portal is intermittently down.", "CLOUD_PLATFORM", "CLOUD", Impact.HIGH, Urgency.HIGH, 500, "Rolled back the config map change and restarted the deployment.", "Invalid environment variable in the config map", 45, 4, "vikram", "sneha"),
                h("Phishing email pretending to be IT", "Received an email pretending to be IT support asking for my password.", "EMAIL", "SECURITY", Impact.MEDIUM, Urgency.HIGH, 1, "Blocked the sender domain, purged the message from all mailboxes and reset the user's password as a precaution.", "Phishing campaign", 40, 5, "rahul", "priya"),
                h("Malware detected on lab PC", "Antivirus shows a threat detected popup on a lab machine.", "DEVICES", "SECURITY", Impact.MEDIUM, Urgency.HIGH, 1, "Isolated the PC, ran a full scan and re-imaged the machine.", "Infected USB drive", 180, 4, "neha", "sneha"),
                h("Suspicious login attempts on admin account", "Many failed logins from unknown IP addresses on an admin account.", "IDENTITY", "SECURITY", Impact.HIGH, Urgency.CRITICAL, 1, "Blocked the source IP range and enforced MFA on all admin accounts.", "Credential stuffing attack", 30, 5, "rahul", "rohan"),
                h("ERP purchase order screen error", "The purchase order screen throws an unexpected error on save.", "ERP_FINANCE", "APPLICATION", Impact.MEDIUM, Urgency.HIGH, 40, "Applied the vendor hotfix for the purchase order module.", "Known vendor bug", 240, 3, "meera", "arjun"),
                h("Payroll report shows wrong totals", "Monthly payroll report totals do not match the ledger.", "ERP_FINANCE", "DATABASE", Impact.HIGH, Urgency.HIGH, 20, "Re-ran the payroll aggregation job after fixing a failed nightly ETL step.", "Nightly ETL failed silently", 200, 4, "vikram", "rohan"),
                h("HR portal leave application not submitting", "The submit button on the leave application does nothing.", "HR_PORTAL", "APPLICATION", Impact.MEDIUM, Urgency.MEDIUM, 100, "Purged the stale JavaScript bundle from the CDN and redeployed the frontend.", "Stale JavaScript bundle cached at the CDN", 80, 4, "isha", "priya"),
                h("HR portal very slow", "The HR portal takes over a minute to load pages.", "HR_PORTAL", "DATABASE", Impact.MEDIUM, Urgency.MEDIUM, 150, "Optimised the leave balance query and added a cache.", "N+1 query on the leave balance page", 260, 3, "meera", "priya"),
                h("Student portal shows 503", "The student portal shows 503 Service Unavailable.", "LMS", "APPLICATION", Impact.HIGH, Urgency.HIGH, 1200, "Scaled the portal from 2 to 6 instances and fixed the health check path.", "Health check misconfiguration after deployment", 35, 5, "meera", "sneha"),
                h("Payment receipt not generated", "Payment succeeded but no receipt was emailed.", "PAYMENT", "APPLICATION", Impact.LOW, Urgency.MEDIUM, 5, "Re-queued the receipt jobs and fixed the template variable.", "Receipt template error", 130, 4, "meera", "ava"),
                h("Network outage in admin building", "No internet or network at all in the admin building.", "NETWORK_CORE", "NETWORK", Impact.HIGH, Urgency.CRITICAL, 300, "Replaced the failed uplink fibre module on the core switch.", "Faulty SFP module", 55, 5, "neha", "rohan"),
                h("DNS resolution failing intermittently", "Some websites do not open and show DNS errors.", "NETWORK_CORE", "NETWORK", Impact.HIGH, Urgency.HIGH, 400, "Restarted the secondary DNS server and fixed zone replication.", "Zone transfer failure", 75, 4, "rahul", "arjun"),
                h("Laptop keyboard not working", "Several laptop keyboard keys are not responding.", "DEVICES", "HARDWARE", Impact.LOW, Urgency.MEDIUM, 1, "Replaced the keyboard under warranty.", "Liquid damage", 300, 3, "kabir", "ava"));

        for (int n = 0; n < history.size(); n++) {
            History h = history.get(n);
            // Spread over the last 30 days, a few hours apart, ending two days ago.
            Instant reported = now.minus(Duration.ofDays(2 + (n * 28L) / history.size()))
                    .minus(Duration.ofHours(n % 7 + 1));
            // about two thirds of past incidents were categorised by the AI classifier
            boolean ai = n % 3 != 0;
            Incident incident = create(h.title(), h.description(), h.service(), h.category(), h.impact(), h.urgency(),
                    h.users(), people.get(h.reporter()), reported, ai, ai ? 0.62 + (n % 5) * 0.07 : null);
            Technician t = tech(h.technician());
            Actor ta = Actor.of(t);
            Actor reporter = Actor.of(incident.getReporter());
            incident.assignTo(t, "Smart recommendation", Actor.SYSTEM, reported.plusSeconds(60));
            int responseTarget = incident.getSla().getResponseMinutes();
            // most incidents were fixed inside their resolution target; roughly one in seven was late
            int target = incident.getSla().getResolutionMinutes();
            int m = n % 7 == 3 ? Math.max(h.minutesToResolve(), target + 15) : Math.min(h.minutesToResolve(), (int) (target * 0.85));
            // most incidents acknowledged in time; every 9th one late (when possible) for realistic SLA statistics
            long ackMinutes = (n % 9 == 8 && responseTarget + 4 < m - 5) ? responseTarget + 4
                    : Math.max(1, Math.min(responseTarget / 3, m / 4));
            Instant ack = reported.plus(Duration.ofMinutes(ackMinutes));
            incident.acknowledge(ta, ack);
            incident.startWork(ta, ack.plusSeconds(60));
            incident.addNote(NoteType.INVESTIGATION, "Investigating: checked logs and recent changes for "
                    + incident.getService().getName() + ".", true, t, ack.plus(Duration.ofMinutes(2)));
            Instant resolved = reported.plus(Duration.ofMinutes(m));
            incident.resolve(new Resolution(h.resolution(), h.rootCause(), t), ta, resolved);
            incident.verify(reporter, resolved.plus(Duration.ofMinutes(25)));
            incident.close("Confirmed working by the reporter", reporter, resolved.plus(Duration.ofMinutes(30)));
            incident.submitFeedback(h.rating(), h.rating() >= 5 ? "Quick and clear, thank you!"
                    : h.rating() == 4 ? "Resolved well." : "Took longer than expected.", reporter,
                    resolved.plus(Duration.ofMinutes(31)));
            saveWithAuditOnly(incident);
        }
    }

    private static History h(String title, String description, String service, String category, Impact impact,
                             Urgency urgency, int users, String resolution, String rootCause, int minutes, int rating,
                             String technician, String reporter) {
        return new History(title, description, service, category, impact, urgency, users, resolution, rootCause,
                minutes, rating, technician, reporter);
    }

    // =========================================================================================
    // Live scenario (what the demo opens on)
    // =========================================================================================

    private void seedLiveScenario(Instant now) {
        // --- A correlated outage: several services that all depend on the Core Database --------------------
        Incident payment = create("Payment transactions failing at checkout",
                "Students and parents cannot complete fee payments. The checkout spins and then shows "
                        + "'transaction failed'. Started around 25 minutes ago.",
                "PAYMENT", "APPLICATION", Impact.CRITICAL, Urgency.CRITICAL, 600, people.get("arjun"), ago(now, 50));
        workOn(payment, "meera", ago(now, 49), ago(now, 47), true);
        payment.addNote(NoteType.INVESTIGATION, "Payment API logs show 'connection pool exhausted' errors talking "
                + "to the core database.", true, tech("meera"), ago(now, 40));
        publishLive(payment);

        Incident hr = create("HR portal timing out on the leave page",
                "The leave application page loads forever and then shows a gateway timeout.",
                "HR_PORTAL", "APPLICATION", Impact.HIGH, Urgency.HIGH, 150, people.get("priya"), ago(now, 40));
        workOn(hr, "vikram", ago(now, 39), ago(now, 33), true);
        publishLive(hr);

        Incident erp = create("ERP finance reports failing with database errors",
                "Month-end finance reports fail with 'could not obtain database connection'.",
                "ERP_FINANCE", "DATABASE", Impact.HIGH, Urgency.HIGH, 40, people.get("rohan"), ago(now, 30));
        publishLive(erp); // left unassigned: it appears in the supervisors' "needs assignment" queue

        Incident duplicate = create("Unable to make payment on fee portal",
                "I am unable to pay my semester fee, the payment keeps failing.",
                "PAYMENT", "APPLICATION", Impact.LOW, Urgency.HIGH, 1, people.get("sneha"), ago(now, 20));
        duplicate.flagSuspectedDuplicate(payment, 0.74, ago(now, 20));
        duplicate.assignTo(tech("meera"), "Smart recommendation", Actor.SYSTEM, ago(now, 19));
        publishLive(duplicate);

        Incident db = create("Database connection pool exhausted",
                "Monitoring alert: core database connections at 100% of the pool, many applications reporting "
                        + "connection timeouts.",
                "CORE_DB", "DATABASE", Impact.CRITICAL, Urgency.HIGH, 500, people.get("vikram"), ago(now, 15));
        workOn(db, "dev", ago(now, 14), ago(now, 12), true);
        publishLive(db);

        // --- A P1 nobody has acknowledged: the SLA monitor will escalate it on its first run --------------
        Incident wifi = create("Campus Wi-Fi down in the library block",
                "No Wi-Fi anywhere in the library block. Around 400 students are affected during exam preparation.",
                "CAMPUS_WIFI", "NETWORK", Impact.CRITICAL, Urgency.CRITICAL, 400, people.get("ava"), ago(now, 12));
        wifi.assignTo(tech("rahul"), "Smart recommendation", Actor.SYSTEM, ago(now, 11));
        publishLive(wifi);

        // --- Waiting for the reporter (SLA clock paused) -------------------------------------------------
        Incident outlook = create("Outlook not syncing new emails",
                "Outlook on my laptop stopped showing new emails since this morning.",
                "EMAIL", "SOFTWARE", Impact.LOW, Urgency.MEDIUM, 1, people.get("ava"), ago(now, 300));
        workOn(outlook, "isha", ago(now, 298), ago(now, 280), true);
        outlook.requestInformation("Do new emails show up when you open Outlook on the web? And which Outlook "
                + "version is installed (File > Office Account)?", Actor.of(tech("isha")), ago(now, 270));
        publishLive(outlook);

        // --- Resolved, waiting for the reporter to verify --------------------------------------------------
        Incident vpn = create("VPN keeps disconnecting every 10 minutes",
                "When working from home the VPN drops roughly every 10 minutes and I lose my session.",
                "NETWORK_CORE", "NETWORK", Impact.MEDIUM, Urgency.MEDIUM, 1, people.get("ava"), ago(now, 26 * 60));
        workOn(vpn, "rahul", ago(now, 26 * 60 - 1), ago(now, 26 * 60 - 20), true);
        vpn.resolve(new Resolution("Updated the VPN client to 5.1 and lowered the MTU to 1400. Please reconnect and "
                        + "confirm the connection stays up.", "Packet fragmentation with the older client", tech("rahul")),
                Actor.of(tech("rahul")), ago(now, 23 * 60));
        publishLive(vpn);

        // --- Ordinary work in progress --------------------------------------------------------------------
        Incident printer = create("Printer on 3rd floor shows paper jam",
                "The printer near the staff room shows a paper jam error, but there is no paper stuck.",
                "PRINTING", "HARDWARE", Impact.LOW, Urgency.MEDIUM, 20, people.get("rohan"), ago(now, 180));
        workOn(printer, "kabir", ago(now, 178), ago(now, 160), true);
        publishLive(printer);

        Incident phishing = create("Phishing email asking staff to verify passwords",
                "Several colleagues received an email that looks like IT asking us to 'verify' our password via a link.",
                "EMAIL", "SECURITY", Impact.MEDIUM, Urgency.HIGH, 200, people.get("priya"), ago(now, 70));
        workOn(phishing, "rahul", ago(now, 69), ago(now, 63), true);
        publishLive(phishing);

        // --- Close to breaching: the SLA monitor raises an "approaching" warning -------------------------
        Incident uploads = create("Lecture video uploads failing on the learning portal",
                "Faculty cannot upload lecture recordings; uploads stop at around 90% with a network error.",
                "LMS", "STORAGE", Impact.HIGH, Urgency.HIGH, 300, people.get("sneha"), ago(now, 100));
        workOn(uploads, "kabir", ago(now, 99), ago(now, 90), true);
        publishLive(uploads);
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private Incident create(String title, String description, String serviceCode, String categoryCode, Impact impact,
                            Urgency urgency, int affectedUsers, User reporter, Instant at) {
        return create(title, description, serviceCode, categoryCode, impact, urgency, affectedUsers, reporter, at, false, null);
    }

    private Incident create(String title, String description, String serviceCode, String categoryCode, Impact impact,
                            Urgency urgency, int affectedUsers, User reporter, Instant at, boolean automatic,
                            Double confidence) {
        ItService service = svc.get(serviceCode);
        Category category = cat.get(categoryCode);
        Incident incident = incidentFactory.create(title, description, reporter, service, impact, urgency,
                affectedUsers, at);
        PriorityAssessment assessment = priorityEngine.assess(impact, urgency, affectedUsers, service, category);
        SlaPolicy policy = slaResolver.resolve(service, assessment.priority());
        incident.classify(new Classification(category, automatic, confidence, assessment, policy), Actor.SYSTEM,
                at.plusSeconds(15));
        return incident;
    }

    private void workOn(Incident incident, String techKey, Instant assignedAt, Instant acknowledgedAt, boolean start) {
        Technician t = tech(techKey);
        incident.assignTo(t, "Smart recommendation", Actor.SYSTEM, assignedAt);
        incident.acknowledge(Actor.of(t), acknowledgedAt);
        if (start) {
            incident.startWork(Actor.of(t), acknowledgedAt.plusSeconds(60));
        }
    }

    /** Historical incidents: write their audit trail only (no notifications for events weeks ago). */
    private void saveWithAuditOnly(Incident incident) {
        Incident saved = incidents.save(incident);
        saved.pullEvents().forEach(auditObserver::onIncidentEvent);
    }

    /** Live incidents: go through every observer so inboxes and the delivery log are populated. */
    private void publishLive(Incident incident) {
        Incident saved = incidents.save(incident);
        publisher.publishAll(saved.pullEvents());
    }

    private static Instant ago(Instant now, long minutes) {
        return now.minus(Duration.ofMinutes(minutes));
    }

    long auditCount() {
        return auditRepository.count();
    }
}
