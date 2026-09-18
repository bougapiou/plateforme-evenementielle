package bf.evenements.plateforme.event;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.common.web.QrImages;
import bf.evenements.plateforme.common.web.Slugs;
import bf.evenements.plateforme.event.dto.EventRequest;
import bf.evenements.plateforme.event.dto.EventResponse;
import bf.evenements.plateforme.event.dto.EventSummary;
import bf.evenements.plateforme.organizer.Organizer;
import bf.evenements.plateforme.organizer.OrganizerService;
import bf.evenements.plateforme.checkin.CheckinRepository;
import bf.evenements.plateforme.invoice.InvoiceRepository;
import bf.evenements.plateforme.payment.PaymentRepository;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.registration.RegistrationRepository;
import bf.evenements.plateforme.stand.StandReservationRepository;
import bf.evenements.plateforme.ticket.TicketOrderRepository;
import bf.evenements.plateforme.ticket.TicketRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final OrganizerService organizerService;
    private final bf.evenements.plateforme.notification.NotificationService notificationService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final TicketOrderRepository ticketOrderRepository;
    private final StandReservationRepository standReservationRepository;
    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final CheckinRepository checkinRepository;
    private final TicketRepository ticketRepository;
    private final InvoiceRepository invoiceRepository;

    // ---------------------------------------------------------------- CRUD

    @Transactional
    public EventResponse create(EventRequest request) {
        Organizer organizer = organizerService.requireActiveOrganizer(currentUser.requireId());
        validateDates(request.dateDebut(), request.dateFin());

        Event event = new Event();
        event.setOrganizer(organizer);
        applyCore(event, request);
        event.setSlug(Slugs.uniqueSlug(request.nom(), s -> !eventRepository.existsBySlug(s)));
        event.setStatut(EventStatus.BROUILLON);
        event = eventRepository.save(event);

        audit("EVENT_CREATED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse update(UUID id, EventRequest request) {
        Event event = loadForManage(id);
        if (!event.getStatut().isEditable() && !currentUser.hasAuthority(Permissions.EVENT_VALIDATE)) {
            throw new BusinessException("EVENT_NOT_EDITABLE",
                    "L'événement ne peut plus être modifié dans son état actuel (" + event.getStatut() + ").");
        }
        validateDates(request.dateDebut(), request.dateFin());
        applyCore(event, request);
        audit("EVENT_UPDATED", event);
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID id) {
        return EventResponse.from(loadForManage(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummary> listMine(@Nullable EventStatus statut, Pageable pageable) {
        UUID userId = currentUser.requireId();
        Specification<Event> spec = Specification.allOf(
                EventSpecifications.ownedByUser(userId),
                EventSpecifications.hasStatus(statut));
        return PageResponse.of(eventRepository.findAll(spec, pageable), EventSummary::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummary> adminList(@Nullable String search, @Nullable EventStatus statut,
                                               @Nullable UUID categoryId, Pageable pageable) {
        Specification<Event> spec = Specification.allOf(
                EventSpecifications.textSearch(search),
                EventSpecifications.hasStatus(statut),
                EventSpecifications.hasCategory(categoryId));
        return PageResponse.of(eventRepository.findAll(spec, pageable), EventSummary::from);
    }

    @Transactional
    public void delete(UUID id) {
        Event event = loadForManage(id);
        boolean admin = currentUser.hasAuthority(Permissions.EVENT_VALIDATE);
        if (!admin && event.getStatut() != EventStatus.BROUILLON
                && event.getStatut() != EventStatus.REFUSE) {
            throw new BusinessException("EVENT_DELETE_FORBIDDEN",
                    "Seuls les brouillons peuvent être supprimés ; utilisez l'annulation.");
        }
        if (hasDependentData(id)) {
            throw new BusinessException("EVENT_HAS_DEPENDENT_DATA",
                    "Cet événement a des billets, inscriptions, paiements ou entrées "
                            + "enregistrées ; utilisez l'annulation plutôt que la suppression, "
                            + "pour conserver cet historique.");
        }
        audit("EVENT_DELETED", event);
        eventRepository.delete(event);
    }

    /** Anything a super admin's hard delete must never silently wipe. */
    private boolean hasDependentData(UUID eventId) {
        return ticketOrderRepository.existsByEventId(eventId)
                || ticketRepository.existsByEventId(eventId)
                || standReservationRepository.existsByEventId(eventId)
                || registrationRepository.existsByEventId(eventId)
                || paymentRepository.existsByEventId(eventId)
                || invoiceRepository.existsByEventId(eventId)
                || checkinRepository.existsByEventId(eventId);
    }

    // ------------------------------------------------------------- workflow

    @Transactional
    public EventResponse submit(UUID id) {
        Event event = loadOwned(id);
        require(event, EnumSet.of(EventStatus.BROUILLON, EventStatus.REFUSE));
        assertReadyForSubmission(event);
        event.setStatut(EventStatus.SOUMIS);
        event.setSoumisLe(Instant.now());
        event.setMotifRefus(null);
        audit("EVENT_SUBMITTED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse validate(UUID id) {
        Event event = load(id);
        require(event, EnumSet.of(EventStatus.SOUMIS));
        event.setStatut(EventStatus.VALIDE);
        event.setValideLe(Instant.now());
        event.setValidePar(currentUser.requireId());
        notifyOrganizer(event, bf.evenements.plateforme.notification.NotificationType.EVENEMENT_VALIDE,
                "Événement validé",
                "Votre événement « " + event.getNom() + " » a été validé. Vous pouvez le publier.");
        audit("EVENT_VALIDATED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse reject(UUID id, String motif) {
        Event event = load(id);
        require(event, EnumSet.of(EventStatus.SOUMIS));
        event.setStatut(EventStatus.REFUSE);
        event.setMotifRefus(motif);
        notifyOrganizer(event, bf.evenements.plateforme.notification.NotificationType.EVENEMENT_REFUSE,
                "Événement refusé",
                "Votre événement « " + event.getNom() + " » a été refusé. Motif : " + motif);
        audit("EVENT_REJECTED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse publish(UUID id) {
        Event event = loadForManage(id);
        require(event, EnumSet.of(EventStatus.VALIDE));
        event.setStatut(EventStatus.PUBLIE);
        event.setPublieLe(Instant.now());
        audit("EVENT_PUBLISHED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse openRegistrations(UUID id) {
        Event event = loadForManage(id);
        require(event, EnumSet.of(EventStatus.PUBLIE, EventStatus.INSCRIPTIONS_FERMEES));
        event.setStatut(EventStatus.INSCRIPTIONS_OUVERTES);
        audit("EVENT_REGISTRATIONS_OPENED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse closeRegistrations(UUID id) {
        Event event = loadForManage(id);
        require(event, EnumSet.of(EventStatus.PUBLIE, EventStatus.INSCRIPTIONS_OUVERTES));
        event.setStatut(EventStatus.INSCRIPTIONS_FERMEES);
        audit("EVENT_REGISTRATIONS_CLOSED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse suspend(UUID id) {
        Event event = load(id);
        if (event.getStatut() == EventStatus.ANNULE || event.getStatut() == EventStatus.TERMINE) {
            throw invalidTransition(event, EventStatus.SUSPENDU);
        }
        event.setStatut(EventStatus.SUSPENDU);
        audit("EVENT_SUSPENDED", event);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse cancel(UUID id) {
        Event event = load(id);
        if (event.getStatut() == EventStatus.TERMINE) {
            throw invalidTransition(event, EventStatus.ANNULE);
        }
        event.setStatut(EventStatus.ANNULE);
        audit("EVENT_CANCELLED", event);
        return EventResponse.from(event);
    }

    // -------------------------------------------------------------- helpers

    /** Loads an event the caller manages (owner) — sub-resource services use this. */
    public Event loadManaged(UUID eventId) {
        return loadForManage(eventId);
    }

    /**
     * QR code (PNG) for the event's public page — meant to be printed on a
     * poster/flyer so a visitor can scan it, land on the event and register
     * or take a ticket straight away. Owner or admin only.
     */
    @Transactional(readOnly = true)
    public byte[] qrPng(UUID id) {
        Event event = loadForManage(id);
        return QrImages.png(publicUrl(event), 320);
    }

    /** Public URL of the event's page, used for the QR code and shared links. */
    public String publicUrl(Event event) {
        return appProperties.frontendBaseUrl() + "/evenements/" + event.getSlug();
    }

    Event load(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", id));
    }

    private Event loadOwned(UUID id) {
        Event event = load(id);
        if (!event.isOwnedBy(currentUser.requireId())) {
            throw new AccessDeniedException("Vous ne gérez pas cet événement.");
        }
        return event;
    }

    private Event loadForManage(UUID id) {
        Event event = load(id);
        if (!event.isOwnedBy(currentUser.requireId())
                && !currentUser.hasAuthority(Permissions.EVENT_VALIDATE)) {
            throw new AccessDeniedException("Accès à l'événement refusé.");
        }
        return event;
    }

    private void require(Event event, Set<EventStatus> allowedFrom) {
        if (!allowedFrom.contains(event.getStatut())) {
            throw new BusinessException("INVALID_EVENT_TRANSITION",
                    "Transition impossible depuis l'état " + event.getStatut() + ".");
        }
    }

    private BusinessException invalidTransition(Event event, EventStatus to) {
        return new BusinessException("INVALID_EVENT_TRANSITION",
                "Transition " + event.getStatut() + " → " + to + " impossible.");
    }

    private void assertReadyForSubmission(Event event) {
        if (!StringUtils.hasText(event.getNom()) || event.getDateDebut() == null
                || event.getDateFin() == null) {
            throw new BusinessException("EVENT_INCOMPLETE",
                    "Renseignez au minimum le nom et les dates avant de soumettre.");
        }
        if (!StringUtils.hasText(event.getVille()) && !StringUtils.hasText(event.getLieu())) {
            throw new BusinessException("EVENT_INCOMPLETE",
                    "Renseignez le lieu ou la ville avant de soumettre.");
        }
    }

    private void validateDates(Instant debut, Instant fin) {
        if (debut != null && fin != null && !fin.isAfter(debut)) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "La date de fin doit être postérieure à la date de début.");
        }
    }

    private void applyCore(Event e, EventRequest r) {
        e.setNom(r.nom().trim());
        e.setSigle(trimToNull(r.sigle()));
        e.setDescriptionCourte(trimToNull(r.descriptionCourte()));
        e.setDescriptionDetaillee(trimToNull(r.descriptionDetaillee()));
        e.setCategory(resolveCategory(r.categoryId()));
        e.setLogoUrl(trimToNull(r.logoUrl()));
        e.setCoverUrl(trimToNull(r.coverUrl()));
        e.setDateDebut(r.dateDebut());
        e.setDateFin(r.dateFin());
        e.setLieu(trimToNull(r.lieu()));
        e.setAdresse(trimToNull(r.adresse()));
        e.setVille(trimToNull(r.ville()));
        e.setPays(StringUtils.hasText(r.pays()) ? r.pays().trim() : "Burkina Faso");
        e.setLatitude(r.latitude());
        e.setLongitude(r.longitude());
        e.setCapaciteMax(r.capaciteMax());
        e.setContactEmail(trimToNull(r.contactEmail()));
        e.setContactTelephone(trimToNull(r.contactTelephone()));
        e.setSiteWeb(trimToNull(r.siteWeb()));
        e.setConditionsParticipation(trimToNull(r.conditionsParticipation()));
        e.setHasActivities(r.hasActivities());
        e.setStandsActifs(r.standsActifs());
        e.setStandsParticuliers(r.standsParticuliers());
        e.setValidationInscription(r.validationInscription());
        e.setInscriptionDebut(r.inscriptionDebut());
        e.setInscriptionFin(r.inscriptionFin());
        e.setReservationDebut(r.reservationDebut());
        e.setReservationFin(r.reservationFin());
    }

    @Nullable
    private EventCategory resolveCategory(@Nullable UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Catégorie", categoryId));
    }

    private void notifyOrganizer(Event event,
                                 bf.evenements.plateforme.notification.NotificationType type,
                                 String titre, String contenu) {
        if (event.getOrganizer() != null && event.getOrganizer().getUser() != null) {
            notificationService.notify(event.getOrganizer().getUser().getId(), type, titre, contenu,
                    "/tableau-de-bord/evenements/" + event.getId());
        }
    }

    private void audit(String action, Event event) {
        auditService.record(currentUser.current().map(u -> u.id()).orElse(null),
                currentUser.current().map(u -> u.email()).orElse(null),
                action, "Event", event.getId().toString(), null,
                "nom=" + event.getNom() + " statut=" + event.getStatut());
    }

    @Nullable
    private static String trimToNull(@Nullable String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
