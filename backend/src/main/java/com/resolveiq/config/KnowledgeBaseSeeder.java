package com.resolveiq.config;

import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.catalog.ItServiceRepository;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.knowledge.ArticleAudience;
import com.resolveiq.knowledge.KnowledgeArticle;
import com.resolveiq.knowledge.KnowledgeArticleRepository;
import com.resolveiq.user.UserRepository;
import com.resolveiq.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Demo knowledge base: self-help articles written from past incidents, two internal runbooks and a
 * draft. Runs whenever the knowledge base is empty, so existing demo databases get it too.
 */
@Service
public class KnowledgeBaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseSeeder.class);

    private final KnowledgeArticleRepository articles;
    private final CategoryRepository categories;
    private final ItServiceRepository services;
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final Clock clock;

    public KnowledgeBaseSeeder(KnowledgeArticleRepository articles, CategoryRepository categories,
                               ItServiceRepository services, IncidentRepository incidents, UserRepository users,
                               Clock clock) {
        this.articles = articles;
        this.categories = categories;
        this.services = services;
        this.incidents = incidents;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void seedIfEmpty() {
        if (articles.count() > 0 || services.count() == 0) {
            return;
        }
        a("VPN keeps disconnecting when working from home",
                "The VPN connects but drops every few minutes, especially on home broadband. Video calls freeze and shared drives disconnect.",
                """
                1. Update the VPN client: open Software Centre → search "VPN" → Update. Versions older than 6.2 fragment large packets.
                2. Restart your laptop after the update.
                3. Still dropping? Plug into your router with a cable for a few minutes to rule out Wi-Fi.
                4. If it continues, report an incident and mention your client version (VPN icon → About).""",
                "NETWORK_CORE", "NETWORK", "vpn, remote, home, disconnect", ArticleAudience.EVERYONE, "VPN connection drops frequently", "rahul", 212, 31, 2, 18);
        a("Outlook stopped receiving new e-mails",
                "Outlook shows no new mail since the morning, the inbox is not updating, or it says \"Disconnected\" at the bottom.",
                """
                1. Check webmail (mail.resolveiq.test). If new mail is there, the problem is on your laptop, not the mail server.
                2. In Outlook: File → Account Settings → Account Settings → select your account → Repair.
                3. If repair does not help: close Outlook, open Control Panel → Mail → Show Profiles → Add a new profile, and set it as default.
                4. Outlook will download your mailbox again; this can take a few minutes on first start.""",
                "EMAIL", "SOFTWARE", "outlook, email, inbox, sync", ArticleAudience.EVERYONE, "Outlook not receiving emails", "kabir", 164, 27, 3, 14);
        a("Account locked after changing your password",
                "After a password change your account locks again and again, even though you are typing the new password correctly.",
                """
                A device is still trying your old password - usually a phone mail app or a saved Wi-Fi network.

                1. On your phone, remove and re-add your work e-mail account, or update its saved password.
                2. Forget and re-join the campus Wi-Fi with the new password.
                3. Wait 15 minutes for the lock to clear, or ask the service desk to unlock you.""",
                "IDENTITY", "AUTHENTICATION", "password, locked, lockout, phone", ArticleAudience.EVERYONE, "Account locked after password change", "isha", 141, 22, 1, 16);
        a("Printing fails with a driver error",
                "Print jobs fail immediately or stay in the queue with \"driver error\" or \"unavailable\", often after a system update.",
                """
                1. Open Software Centre and install "Campus Printers" again - it reinstalls the correct driver.
                2. Remove any old printer entries with the same name under Settings → Printers.
                3. Print a test page. If the printer itself shows an error (paper jam, toner), report an incident with the printer's location.""",
                "PRINTING", "SOFTWARE", "printer, print, driver, queue", ArticleAudience.EVERYONE, "Cannot print from laptop", "isha", 98, 17, 2, 11);
        a("Mailbox full - free up space or turn on archiving",
                "You get \"mailbox quota exceeded\" warnings and new e-mails bounce back to the sender.",
                """
                1. Empty Deleted Items and Junk.
                2. Sort by size and delete or save large attachments to your drive.
                3. Online archiving moves old mail out of your inbox automatically - request it from the service desk (it is free and takes a few minutes).""",
                "EMAIL", "STORAGE", "mailbox, quota, storage, archive", ArticleAudience.EVERYONE, "Mailbox full, cannot receive mail", "kabir", 76, 12, 0, 9);
        a("Laptop won't turn on",
                "The laptop does not start, even when plugged in. The charging light may be off.",
                """
                1. Try a different power socket and check the charger's light.
                2. Hold the power button for 30 seconds, then press it once normally.
                3. If you have another compatible charger, try it - a faulty adapter is the most common cause.
                4. Still nothing? Report an incident; we can lend you a laptop while yours is repaired.""",
                "DEVICES", "HARDWARE", "laptop, power, charger, battery, boot", ArticleAudience.EVERYONE, "Laptop not powering on", "kabir", 88, 14, 2, 7);
        a("Slow Wi-Fi in the library and lecture halls",
                "Internet is very slow in crowded areas, pages take a long time to load, or the connection keeps dropping.",
                """
                1. Connect to "Campus-5G" rather than "Campus" - the 5 GHz network is much less congested.
                2. Turn Wi-Fi off and on to roam to the nearest access point.
                3. If one room is always slow, report an incident with the room number so we can check its access point.""",
                "CAMPUS_WIFI", "NETWORK", "wifi, wireless, slow, internet, library", ArticleAudience.EVERYONE, "Slow internet in the library", "dev", 120, 19, 4, 10);
        a("How to spot and report a phishing e-mail",
                "You received an e-mail asking for your password, to \"verify\" your account, or to open an unexpected attachment.",
                """
                IT will never ask for your password by e-mail.

                1. Do not click links or open attachments.
                2. Use "Report phishing" in Outlook, or forward the message as an attachment to security@resolveiq.test.
                3. If you already clicked or typed your password, change your password now and report a Security incident - speed matters.""",
                "EMAIL", "SECURITY", "phishing, scam, security, password", ArticleAudience.EVERYONE, "Phishing email pretending to be IT", "rahul", 203, 41, 1, 12);
        a("Installing software without admin rights",
                "An installer asks for administrator rights you do not have.",
                """
                Most approved software is available without admin rights:

                1. Open Software Centre from the Start menu.
                2. Search for the application and click Install.
                3. Not listed? Report an incident with the software name and why you need it; we review requests within two working days.""",
                "DEVICES", "SOFTWARE", "install, software, admin, licence", ArticleAudience.EVERYONE, "Statistics software installation blocked", "kabir", 67, 13, 1, 8);
        a("Recovering deleted files from the shared drive",
                "A file or folder disappeared from a shared or department drive.",
                """
                Shared drives keep snapshots for 30 days.

                1. Right-click the folder that contained the file → Properties → Previous Versions.
                2. Pick a version from before the deletion → Restore (or Open, to copy just one file).
                3. Can't see Previous Versions, or it is older than 30 days? Report an incident with the full folder path.""",
                "CLOUD_STORAGE", "STORAGE", "deleted, restore, shared drive, files, snapshot", ArticleAudience.EVERYONE, "Files missing from shared drive", "dev", 58, 11, 0, 6);
        a("Large lecture video uploads fail near 100%",
                "Uploading a recorded lecture to the learning portal stops at 99% or fails with a timeout.",
                """
                1. Videos up to 2 GB upload directly. For larger files, export at 720p or split the recording.
                2. Use a wired connection for long uploads.
                3. If a file under 2 GB still fails, report an incident with the file size and the time of the attempt.""",
                "LMS", "STORAGE", "video, upload, lms, lecture", ArticleAudience.EVERYONE, "Lecture video upload failing", "dev", 45, 9, 1, 5);
        a("Runbook: runaway query pegs Core DB CPU",
                "Core database CPU at 100%, application queries timing out across several services at once.",
                """
                1. Check pg_stat_activity for long-running queries: SELECT pid, now() - query_start, query FROM pg_stat_activity WHERE state = 'active' ORDER BY 2 DESC;
                2. Cancel (pg_cancel_backend) reporting queries running for more than 5 minutes; terminate only if cancel fails.
                3. Check for missing indexes on the tables involved (EXPLAIN ANALYZE).
                4. Declare a major incident if more than two dependent services are affected, and link their incidents.""",
                "CORE_DB", "DATABASE", "database, cpu, postgres, query, runbook", ArticleAudience.IT_STAFF, "Core database CPU at 100%", "vikram", 34, 8, 0, 0);
        a("Runbook: expired SAML certificate breaks SSO",
                "Every SSO login loops back to the login page; identity provider logs show signature validation failures.",
                """
                1. Check the signing certificate's expiry in the identity provider admin console.
                2. Roll over to the standby certificate and publish the new metadata.
                3. Update the certificate fingerprint in each service provider (HR, ERP, LMS).
                4. Set a calendar reminder 30 days before the next expiry.""",
                "IDENTITY", "AUTHENTICATION", "sso, saml, certificate, runbook", ArticleAudience.IT_STAFF, "Cannot log in with SSO", "rahul", 21, 6, 0, 0);
        KnowledgeArticle draft = new KnowledgeArticle("Excel crashes when opening large reports",
                "Excel closes without warning when opening large finance workbooks.",
                "Start Excel in safe mode (hold Ctrl while opening), then disable COM add-ins under File → Options → Add-ins. Install the latest Office update.",
                categories.findByCodeIgnoreCase("SOFTWARE").orElse(null), services.findByCodeIgnoreCase("DEVICES").orElse(null),
                "excel, crash, office", ArticleAudience.EVERYONE, source("Excel crashes when opening large files").orElse(null),
                users.findByEmailIgnoreCase("isha@resolveiq.test").orElse(null));
        articles.save(draft);
        log.info("Knowledge base ready: {} articles", articles.count());
    }

    private void a(String title, String symptoms, String solution, String service, String category, String tags,
                   ArticleAudience audience, String sourceTitle, String authorKey, int views, int helpful, int unhelpful,
                   int deflections) {
        User author = users.findByEmailIgnoreCase(authorKey + "@resolveiq.test").orElse(null);
        KnowledgeArticle a = new KnowledgeArticle(title, symptoms, solution.strip(),
                categories.findByCodeIgnoreCase(category).orElse(null), services.findByCodeIgnoreCase(service).orElse(null),
                tags, audience, source(sourceTitle).orElse(null), author);
        a.publish(clock.instant().minus(Duration.ofDays(20)));
        a.importStatistics(views, helpful, unhelpful, deflections);
        articles.save(a);
    }

    private Optional<Incident> source(String title) {
        return incidents.findAll().stream().filter(i -> i.getTitle().equals(title)).findFirst();
    }
}
