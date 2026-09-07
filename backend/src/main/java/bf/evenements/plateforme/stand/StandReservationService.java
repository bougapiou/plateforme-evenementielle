package bf.evenements.plateforme.stand;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.common.web.References;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.stand.dto.CreateStandReservationRequest;
import bf.evenements.plateforme.stand.dto.StandReservationResponse;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.structure.StructureMember;
import bf.evenements.plateforme.structure.StructureMemberRepository;
import bf.evenements.plateforme.structure.StructureRepository;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StandReservationService {

    /** Temporary hold before payment. */
    static final Duration HOLD = Duration.ofMinutes(15);

    private final StandReservationRepository reservationRepository;
    private final StandRepository standRepository;
    private final EventRepository eventRepository;
    private final StructureRepository structureRepository;
    private final StructureMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional
    public StandReservationResponse reserve(CreateStandReservationRequest request) {
        User me = userRepository.findById(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", request.eventId()));

        if (!event.isStandsActifs()) {
            throw new BusinessException("STANDS_DISABLED",
                    "La réservation de stands n'est pas activée pour cet événement.");
        }
        Instant now = Instant.now();
        if (!event.getStatut().isPubliclyVisible()) {
            throw new BusinessException("EVENT_NOT_OPEN", "Cet événement n'accepte pas de réservations.");
        }
        if (event.getReservationDebut() != null && now.isBefore(event.getReservationDebut())) {
            throw new BusinessException("RESERVATIONS_NOT_OPEN",
                    "La période de réservation des stands n'a pas commencé.");
        }
        if (event.getReservationFin() != null && now.isAfter(event.getReservationFin())) {
            throw new BusinessException("RESERVATIONS_CLOSED",
                    "La période de réservation des stands est terminée.");
        }

        Stand stand = standRepository.findByIdForUpdate(request.standId())
                .orElseThrow(() -> ResourceNotFoundException.of("Stand", request.standId()));
        if (!stand.getEvent().getId().equals(event.getId())) {
            throw new BusinessException("STAND_EVENT_MISMATCH", "Ce stand n'appartient pas à l'événement.");
        }
        if (stand.getStatut() == StandStatus.INDISPONIBLE) {
            throw new ConflictException("STAND_UNAVAILABLE", "Ce stand n'est pas disponible.");
        }
        if (!reservationRepository.findActiveByStand(stand.getId()).isEmpty()) {
            throw new ConflictException("STAND_ALREADY_RESERVED",
                    "Ce stand vient d'être réservé par une autre structure.");
        }

        StandType type = stand.getStandType();
        StandReservation reservation = new StandReservation();
        reservation.setReference(References.unique("STD", 8, reservationRepository::existsByReference));
        reservation.setNumeroReservation(
                References.unique("RS", 8, reservationRepository::existsByNumeroReservation));
        reservation.setEvent(event);
        reservation.setStand(stand);
        reservation.setStandType(type);
        reservation.setUser(me);
        reservation.setStructure(resolveStructure(request.structureId(), me.getId()));
        reservation.setMontant(type.getPrixMontant());
        reservation.setDevise(type.getDevise());
        reservation.setInformations(request.informations());
        reservation.setStatut(StandReservationStatus.RESERVE_TEMP);
        reservation.setHoldExpireLe(now.plus(HOLD));
        reservation.setDateLimitePaiement(now.plus(HOLD));

        try {
            reservation = reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            // partial unique index tripped: a concurrent reservation won the race
            throw new ConflictException("STAND_ALREADY_RESERVED",
                    "Ce stand vient d'être réservé par une autre structure.");
        }

        auditService.record(me.getId(), me.getEmail(), "STAND_RESERVED", "StandReservation",
                reservation.getId().toString(), null,
                "stand=" + stand.getNumero() + " ref=" + reservation.getReference());

        if (type.getPrixMontant().signum() == 0) {
            confirmInternal(reservation);
        }
        return StandReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public PageResponse<StandReservationResponse> myReservations(Pageable pageable) {
        return PageResponse.of(
                reservationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.requireId(), pageable),
                StandReservationResponse::from);
    }

    @Transactional(readOnly = true)
    public StandReservationResponse get(UUID id) {
        return StandReservationResponse.from(loadForActor(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<StandReservationResponse> forEvent(UUID eventId, Pageable pageable) {
        eventService.loadManaged(eventId);
        return PageResponse.of(
                reservationRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable),
                StandReservationResponse::from);
    }

    @Transactional
    public StandReservationResponse cancel(UUID id) {
        StandReservation reservation = loadForActor(id);
        if (reservation.getStatut() == StandReservationStatus.CONFIRME
                || reservation.getStatut() == StandReservationStatus.PAYE) {
            throw new BusinessException("RESERVATION_PAID",
                    "Une réservation payée ne peut pas être annulée ici.");
        }
        reservation.setStatut(StandReservationStatus.ANNULE);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "STAND_RESERVATION_CANCELLED", "StandReservation", id.toString(), null, null);
        return StandReservationResponse.from(reservation);
    }

    @Transactional
    public StandReservationResponse confirmSandboxPayment(UUID id) {
        StandReservation reservation = loadForActor(id);
        if (reservation.getStatut() == StandReservationStatus.CONFIRME) {
            return StandReservationResponse.from(reservation);
        }
        if (!reservation.getStatut().isPending()) {
            throw new BusinessException("RESERVATION_NOT_PENDING",
                    "Réservation non payable dans cet état (" + reservation.getStatut() + ").");
        }
        confirmInternal(reservation);
        return StandReservationResponse.from(reservation);
    }

    /** Called by the payment module on success. */
    @Transactional
    public void markPaid(UUID reservationId) {
        StandReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Réservation", reservationId));
        if (reservation.getStatut() == StandReservationStatus.CONFIRME) {
            return;
        }
        if (!reservation.getStatut().isPending()) {
            throw new BusinessException("RESERVATION_NOT_PENDING",
                    "Réservation dans un état incompatible avec le paiement.");
        }
        confirmInternal(reservation);
    }

    @Transactional
    public int expireStaleReservations() {
        List<StandReservation> expired = reservationRepository.findExpired(Instant.now());
        expired.forEach(r -> r.setStatut(StandReservationStatus.EXPIRE));
        return expired.size();
    }

    // --- helpers ---

    private void confirmInternal(StandReservation reservation) {
        reservation.setStatut(StandReservationStatus.CONFIRME);
        reservation.setPayeLe(Instant.now());
        auditService.record(reservation.getUser().getId(), reservation.getUser().getEmail(),
                "STAND_RESERVATION_CONFIRMED", "StandReservation", reservation.getId().toString(),
                null, "ref=" + reservation.getReference());
    }

    private StandReservation loadForActor(UUID id) {
        StandReservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Réservation", id));
        UUID me = currentUser.requireId();
        boolean owner = reservation.belongsTo(me);
        boolean organiser = reservation.getEvent().isOwnedBy(me);
        boolean admin = currentUser.hasAuthority(bf.evenements.plateforme.rbac.Permissions.PAYMENT_MANAGE);
        if (!owner && !organiser && !admin) {
            throw new AccessDeniedException("Accès à la réservation refusé.");
        }
        return reservation;
    }

    @Nullable
    private Structure resolveStructure(@Nullable UUID structureId, UUID userId) {
        if (structureId == null) {
            return null;
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
}
