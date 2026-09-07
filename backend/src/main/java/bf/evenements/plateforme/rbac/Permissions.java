package bf.evenements.plateforme.rbac;

import java.util.List;

/**
 * Canonical catalogue of permission names. Referenced from {@code @PreAuthorize}
 * expressions and seeded into the database on startup.
 */
public final class Permissions {

    private Permissions() {
    }

    // --- Users / RBAC ---
    public static final String USER_READ = "USER_READ";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";
    public static final String AUDIT_READ = "AUDIT_READ";

    // --- Structures / organisers ---
    public static final String STRUCTURE_READ = "STRUCTURE_READ";
    public static final String STRUCTURE_MANAGE = "STRUCTURE_MANAGE";
    public static final String ORGANIZER_MANAGE = "ORGANIZER_MANAGE";

    // --- Events ---
    public static final String EVENT_READ = "EVENT_READ";
    public static final String EVENT_CREATE = "EVENT_CREATE";
    public static final String EVENT_UPDATE = "EVENT_UPDATE";
    public static final String EVENT_DELETE = "EVENT_DELETE";
    public static final String EVENT_PUBLISH = "EVENT_PUBLISH";
    public static final String EVENT_VALIDATE = "EVENT_VALIDATE";
    public static final String EVENT_CATEGORY_MANAGE = "EVENT_CATEGORY_MANAGE";

    // --- Tickets / stands / registrations ---
    public static final String TICKET_MANAGE = "TICKET_MANAGE";
    public static final String TICKET_PURCHASE = "TICKET_PURCHASE";
    public static final String STAND_MANAGE = "STAND_MANAGE";
    public static final String STAND_RESERVE = "STAND_RESERVE";
    public static final String REGISTRATION_CREATE = "REGISTRATION_CREATE";
    public static final String REGISTRATION_MANAGE = "REGISTRATION_MANAGE";

    // --- Payments / finance ---
    public static final String PAYMENT_READ = "PAYMENT_READ";
    public static final String PAYMENT_MANAGE = "PAYMENT_MANAGE";
    public static final String COMMISSION_MANAGE = "COMMISSION_MANAGE";

    // --- Operations ---
    public static final String CHECKIN_SCAN = "CHECKIN_SCAN";
    public static final String STATS_GLOBAL_READ = "STATS_GLOBAL_READ";
    public static final String STATS_OWN_READ = "STATS_OWN_READ";
    public static final String NOTIFICATION_MANAGE = "NOTIFICATION_MANAGE";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

    /** Every known permission, used by the seeder. */
    public static final List<String> ALL = List.of(
            USER_READ, USER_MANAGE, ROLE_MANAGE, AUDIT_READ,
            STRUCTURE_READ, STRUCTURE_MANAGE, ORGANIZER_MANAGE,
            EVENT_READ, EVENT_CREATE, EVENT_UPDATE, EVENT_DELETE, EVENT_PUBLISH,
            EVENT_VALIDATE, EVENT_CATEGORY_MANAGE,
            TICKET_MANAGE, TICKET_PURCHASE, STAND_MANAGE, STAND_RESERVE,
            REGISTRATION_CREATE, REGISTRATION_MANAGE,
            PAYMENT_READ, PAYMENT_MANAGE, COMMISSION_MANAGE,
            CHECKIN_SCAN, STATS_GLOBAL_READ, STATS_OWN_READ,
            NOTIFICATION_MANAGE, SETTINGS_MANAGE);
}
