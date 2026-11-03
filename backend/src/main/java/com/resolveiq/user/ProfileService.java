package com.resolveiq.user;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * People maintain their own contact details. Their role, e-mail and access level are never
 * editable here - only an administrator can change those.
 */
@Service
@Transactional
public class ProfileService {

    private final UserRepository users;
    private final AuditService audit;

    public ProfileService(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    public User update(User caller, String fullName, String phone, String department, String location,
                       Set<String> skills, boolean completingFirstSignIn) {
        User user = users.findById(caller.getId()).orElseThrow();
        if (fullName != null && !fullName.isBlank()) {
            user.rename(fullName);
        }
        user.updateProfile(phone, department, location);
        if (skills != null && user instanceof Technician t) {
            Set<String> clean = skills.stream().map(String::trim).filter(s -> !s.isEmpty())
                    .map(String::toUpperCase).collect(Collectors.toSet());
            if (clean.isEmpty()) {
                throw new BusinessRuleViolationException("Keep at least one skill so incidents can be routed to you");
            }
            t.replaceSkills(clean);
        }
        if (completingFirstSignIn) {
            user.completeProfile();
        }
        audit.recordAdminAction(user, AuditAction.PROFILE_UPDATED, null, null,
                completingFirstSignIn ? user.getFullName() + " completed their profile" : user.getFullName() + " updated their profile");
        return users.save(user);
    }
}
