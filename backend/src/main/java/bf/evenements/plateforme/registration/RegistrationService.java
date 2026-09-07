package bf.evenements.plateforme.registration;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.events.PaymentSucceededEvent;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.common.web.References;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.registration.dto.RegisterParticipationRequest;
import bf.evenements.plateforme.registration.dto.RegistrationResponse;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.structure.StructureMember;
import bf.evenements.plateforme.structure.StructureMemberRepository;
import bf.evenements.plateforme.structure.StructureRepository;
import bf.evenements.plateforme.ticket.TicketOrder;
import bf.evenements.plateforme.ticket.TicketOrderService;
import bf.evenements.plateforme.ticket.TicketOrderStatus;
import bf.evenements.plateforme.ticket.dto.CreateOrderRequest;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final StructureRepository structureRepository;
    private final StructureMemberRepository memberRepository;
    private final TicketOrderService ticketOrderService;
    private final EventService eventService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional
    public RegistrationResponse register(UUID eventId, RegisterParticipationRequest request) {
        User me = userRepository.findById(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));

        Instant now = Instant.now();
        if (!event.getStatut().acceptsRegistrations() || !event.withinRegistrationWindow(now)) {
            throw new BusinessException("REGISTRATIONS_CLOSED",
                    "Les inscriptions ne sont pas ouvertes pour cet événement.");
        }
        if (registrationRepository.existsByEventIdAndUserIdAndStatutIn(eventId, me.getId(),
                List.of(RegistrationStatus.EN_ATTENTE, RegistrationStatus.CONFIRMEE))) {
            throw new ConflictException("ALREADY_REGISTERED",
                    "Vous avez déjà une inscription en cours pour cet événement.");
        }

        Registration registration = new Registration();
        registration.setReference(References.unique("INS", 8, registrationRepository::existsByReference));
        registration.setEvent(event);
        registration.setUser(me);
        registration.setType(request.type());
        registration.setStructure(request.type() == RegistrationType.STRUCTURE
                ? resolveStructure(request.structureId(), me.getId()) : null);
        registration.setContactNom(orDefault(request.contactNom(), me.getFullName()));
        registration.setContactEmail(orDefault(request.contactEmail(), me.getEmail()));
        registration.setContactTelephone(orDefault(request.contactTelephone(), me.getPhone()));
        registration.setInformations(request.informations());

        List<RegisterParticipationRequest.ParticipantInput> inputs =
                request.participants() == null || request.participants().isEmpty()
                        ? List.of(new RegisterParticipationRequest.ParticipantInput(
                                me.getLastName(), me.getFirstName(), me.getEmail(), me.getPhone(), null))
                        : request.participants();
        for (var in : inputs) {
            Participant p = new Participant();
            p.setNom(in.nom().trim());
            p.setPrenom(in.prenom());
            p.setEmail(in.email());
            p.setTelephone(in.telephone());
            p.setFonction(in.fonction());
            registration.addParticipant(p);
        }
        registration.setNombreParticipants(inputs.size());
        registration.setStatut(RegistrationStatus.EN_ATTENTE);
        registration = registrationRepository.save(registration);

        // Optional ticket purchase attached to this registration
        if (request.tickets() != null && !request.tickets().isEmpty()) {
            List<CreateOrderRequest.Line> lines = request.tickets().stream()
                    .map(t -> new CreateOrderRequest.Line(t.eventTicketId(), t.quantite()))
                    .toList();
            TicketOrder order = ticketOrderService.createOrderInternal(new CreateOrderRequest(
                    eventId, registration.getStructure() != null
                            ? registration.getStructure().getId() : null,
                    registration.getContactNom(), registration.getContactEmail(), lines));
            registration.setTicketOrder(order);
            if (order.getStatut() == TicketOrderStatus.PAYEE) {
                confirmIfAllowed(registration, event);
            }
        } else {
            // free registration
            confirmIfAllowed(registration, event);
        }

        auditService.record(me.getId(), me.getEmail(), "REGISTRATION_CREATED", "Registration",
                registration.getId().toString(), null,
                "ref=" + registration.getReference() + " statut=" + registration.getStatut());
        return RegistrationResponse.from(registration);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> myRegistrations(Pageable pageable) {
        return PageResponse.of(
                registrationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.requireId(), pageable),
                RegistrationResponse::from);
    }

    @Transactional(readOnly = true)
    public RegistrationResponse get(UUID id) {
        return RegistrationResponse.from(loadForActor(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> forEvent(UUID eventId, Pageable pageable) {
        eventService.loadManaged(eventId);
        return PageResponse.of(
                registrationRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable),
                RegistrationResponse::from);
    }

    @Transactional
    public RegistrationResponse cancel(UUID id) {
        Registration r = loadForActor(id);
        if (r.getStatut() == RegistrationStatus.CONFIRMEE && r.getTicketOrder() != null
                && r.getTicketOrder().getStatut() == TicketOrderStatus.PAYEE) {
            throw new BusinessException("REGISTRATION_PAID",
                    "Une inscription payée ne peut pas être annulée ici (contactez l'organisateur).");
        }
        r.setStatut(RegistrationStatus.ANNULEE);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "REGISTRATION_CANCELLED", "Registration", id.toString(), null, null);
        return RegistrationResponse.from(r);
    }

    @Transactional
    public RegistrationResponse organiserConfirm(UUID id) {
        Registration r = load(id);
        eventService.loadManaged(r.getEvent().getId());
        if (r.getStatut() != RegistrationStatus.EN_ATTENTE) {
            throw new BusinessException("NOT_PENDING", "Cette inscription n'est pas en attente.");
        }
        r.setStatut(RegistrationStatus.CONFIRMEE);
        r.setConfirmeeLe(Instant.now());
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "REGISTRATION_CONFIRMED", "Registration", id.toString(), null, null);
        return RegistrationResponse.from(r);
    }

    @Transactional
    public RegistrationResponse organiserReject(UUID id, String motif) {
        Registration r = load(id);
        eventService.loadManaged(r.getEvent().getId());
        r.setStatut(RegistrationStatus.REFUSEE);
        r.setMotifRefus(motif);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "REGISTRATION_REJECTED", "Registration", id.toString(), null, motif);
        return RegistrationResponse.from(r);
    }

    /** Confirms a registration once its linked ticket order is paid. */
    @EventListener
    @Transactional
    public void onPayment(PaymentSucceededEvent event) {
        if (!PaymentSucceededEvent.TICKET_ORDER.equals(event.targetType())) {
            return;
        }
        registrationRepository.findByTicketOrderId(event.targetId()).ifPresent(r -> {
            if (r.getStatut() == RegistrationStatus.EN_ATTENTE) {
                confirmIfAllowed(r, r.getEvent());
            }
        });
    }

    // --- helpers ---

    private void confirmIfAllowed(Registration r, Event event) {
        if (event.isValidationInscription()) {
            r.setStatut(RegistrationStatus.EN_ATTENTE); // organiser must validate
        } else {
            r.setStatut(RegistrationStatus.CONFIRMEE);
            r.setConfirmeeLe(Instant.now());
        }
    }

    private Registration load(UUID id) {
        return registrationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Inscription", id));
    }

    private Registration loadForActor(UUID id) {
        Registration r = load(id);
        UUID me = currentUser.requireId();
        boolean owner = r.belongsTo(me);
        boolean organiser = r.getEvent().isOwnedBy(me);
        boolean admin = currentUser.hasAuthority(
                bf.evenements.plateforme.rbac.Permissions.REGISTRATION_MANAGE);
        if (!owner && !organiser && !admin) {
            throw new AccessDeniedException("Accès à l'inscription refusé.");
        }
        return r;
    }

    @Nullable
    private Structure resolveStructure(@Nullable UUID structureId, UUID userId) {
        if (structureId == null) {
            throw new BusinessException("STRUCTURE_REQUIRED",
                    "Une inscription de type STRUCTURE doit préciser la structure.");
        }
        Structure structure = structureRepository.findById(structureId)
                .orElseThrow(() -> ResourceNotFoundException.of("Structure", structureId));
        boolean member = memberRepository.findByStructureIdAndUserId(structureId, userId)
                .filter(StructureMember::isActive).isPresent();
        if (!member) {
            throw new AccessDeniedException("Vous n'êtes pas membre de cette structure.");
        }
        return structure;
    }

    @Nullable
    private static String orDefault(@Nullable String value, @Nullable String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
