package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.events.PaymentSucceededEvent;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.common.web.References;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.structure.StructureMember;
import bf.evenements.plateforme.structure.StructureMemberRepository;
import bf.evenements.plateforme.structure.StructureRepository;
import bf.evenements.plateforme.ticket.dto.CreateOrderRequest;
import bf.evenements.plateforme.ticket.dto.TicketOrderResponse;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketOrderService {

    /** How long a pending order holds its reserved quota. */
    static final Duration HOLD = Duration.ofMinutes(30);

    private final TicketOrderRepository orderRepository;
    private final EventTicketRepository ticketRepository;
    private final TicketRepository ticketEntityRepository;
    private final EventRepository eventRepository;
    private final StructureRepository structureRepository;
    private final StructureMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final bf.evenements.plateforme.qrcode.QrCodeService qrCodeService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    // -------------------------------------------------------------- create

    @Transactional
    public TicketOrderResponse createOrder(CreateOrderRequest request) {
        return TicketOrderResponse.from(createOrderInternal(request));
    }

    /** Creates the order and returns the entity — used by the registration module. */
    @Transactional
    public TicketOrder createOrderInternal(CreateOrderRequest request) {
        User buyer = userRepository.findById(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", request.eventId()));

        Instant now = Instant.now();
        if (!event.getStatut().acceptsRegistrations() || !event.withinRegistrationWindow(now)) {
            throw new BusinessException("REGISTRATIONS_CLOSED",
                    "Les inscriptions ne sont pas ouvertes pour cet événement.");
        }

        TicketOrder order = new TicketOrder();
        order.setEvent(event);
        order.setUser(buyer);
        order.setStructure(resolveStructure(request.structureId(), buyer.getId()));
        order.setAcheteurNom(request.acheteurNom() != null ? request.acheteurNom().trim()
                : buyer.getFullName());
        order.setAcheteurEmail(request.acheteurEmail() != null ? request.acheteurEmail().trim()
                : buyer.getEmail());
        order.setReference(References.unique("CMD", 8, orderRepository::existsByReference));

        String devise = null;
        BigDecimal total = BigDecimal.ZERO;

        for (CreateOrderRequest.Line line : request.lignes()) {
            EventTicket ticket = ticketRepository.findByIdForUpdate(line.eventTicketId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Catégorie de ticket",
                            line.eventTicketId()));
            if (!ticket.getEvent().getId().equals(event.getId())) {
                throw new BusinessException("TICKET_EVENT_MISMATCH",
                        "Une catégorie de ticket n'appartient pas à cet événement.");
            }
            int qty = line.quantite();
            if (!ticket.onSale(now)) {
                throw new BusinessException("TICKET_NOT_ON_SALE",
                        "La catégorie « " + ticket.getNom() + " » n'est pas en vente.");
            }
            if (qty > ticket.quantiteRestante()) {
                throw new BusinessException("QUOTA_EXCEEDED",
                        "Il ne reste que " + ticket.quantiteRestante() + " billet(s) « "
                                + ticket.getNom() + " ».");
            }
            int already = orderRepository.quantityAlreadyOrdered(ticket.getId(), buyer.getId());
            if (already + qty > ticket.getLimiteParUtilisateur()) {
                throw new BusinessException("PER_USER_LIMIT",
                        "Limite de " + ticket.getLimiteParUtilisateur() + " billet(s) « "
                                + ticket.getNom() + " » par personne.");
            }
            if (devise == null) {
                devise = ticket.getDevise();
            } else if (!devise.equals(ticket.getDevise())) {
                throw new BusinessException("MIXED_CURRENCIES",
                        "Une commande ne peut pas mélanger plusieurs devises.");
            }

            ticket.setQuantiteReservee(ticket.getQuantiteReservee() + qty);

            TicketOrderLine orderLine = new TicketOrderLine();
            orderLine.setEventTicket(ticket);
            orderLine.setQuantite(qty);
            orderLine.setPrixUnitaire(ticket.getPrixMontant());
            order.addLine(orderLine);
            total = total.add(ticket.getPrixMontant().multiply(BigDecimal.valueOf(qty)));
        }

        order.setDevise(devise);
        order.setMontantTotal(total);
        order.setExpireLe(now.plus(HOLD));
        order = orderRepository.save(order);

        auditService.record(buyer.getId(), buyer.getEmail(), "TICKET_ORDER_CREATED", "TicketOrder",
                order.getId().toString(), null, "ref=" + order.getReference() + " total=" + total);

        // Free orders are confirmed immediately.
        if (total.signum() == 0) {
            confirmPaymentInternal(order);
        }
        return order;
    }

    // ------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public PageResponse<TicketOrderResponse> myOrders(Pageable pageable) {
        return PageResponse.of(
                orderRepository.findByUserIdOrderByCreatedAtDesc(currentUser.requireId(), pageable),
                TicketOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public TicketOrderResponse get(UUID id) {
        return TicketOrderResponse.from(loadForActor(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketOrderResponse> forEvent(UUID eventId, Pageable pageable) {
        eventService.loadManaged(eventId);
        return PageResponse.of(
                orderRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable),
                TicketOrderResponse::from);
    }

    // ------------------------------------------------------- state changes

    @Transactional
    public TicketOrderResponse cancel(UUID id) {
        TicketOrder order = loadForActor(id);
        if (!order.isPending()) {
            throw new BusinessException("ORDER_NOT_PENDING",
                    "Seule une commande en attente peut être annulée.");
        }
        releaseReservations(order);
        order.setStatut(TicketOrderStatus.ANNULEE);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "TICKET_ORDER_CANCELLED", "TicketOrder", id.toString(), null, null);
        return TicketOrderResponse.from(order);
    }

    /** Sandbox helper: confirms an order without a real payment (dev / demo). */
    @Transactional
    public TicketOrderResponse confirmSandboxPayment(UUID id) {
        TicketOrder order = loadForActor(id);
        if (order.getStatut() == TicketOrderStatus.PAYEE) {
            return TicketOrderResponse.from(order);
        }
        if (!order.isPending()) {
            throw new BusinessException("ORDER_NOT_PENDING", "Commande non payable dans cet état.");
        }
        confirmPaymentInternal(order);
        return TicketOrderResponse.from(order);
    }

    /** Entry point used by the payment module once a payment succeeds. */
    @Transactional
    public void markPaid(UUID orderId) {
        TicketOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Commande", orderId));
        if (order.getStatut() == TicketOrderStatus.PAYEE) {
            return;
        }
        if (!order.isPending()) {
            throw new BusinessException("ORDER_NOT_PENDING",
                    "Commande dans un état incompatible avec le paiement (" + order.getStatut() + ").");
        }
        confirmPaymentInternal(order);
    }

    @Transactional
    public int expireStaleOrders() {
        List<TicketOrder> stale = orderRepository.findByStatutAndExpireLeBefore(
                TicketOrderStatus.EN_ATTENTE, Instant.now());
        for (TicketOrder order : stale) {
            releaseReservations(order);
            order.setStatut(TicketOrderStatus.EXPIREE);
        }
        return stale.size();
    }

    // -------------------------------------------------------------- helpers

    private void confirmPaymentInternal(TicketOrder order) {
        for (TicketOrderLine line : order.getLines()) {
            EventTicket ticket = ticketRepository.findByIdForUpdate(line.getEventTicket().getId())
                    .orElseThrow();
            int qty = line.getQuantite();
            ticket.setQuantiteReservee(Math.max(0, ticket.getQuantiteReservee() - qty));
            ticket.setQuantiteVendue(ticket.getQuantiteVendue() + qty);
            for (int i = 0; i < qty; i++) {
                Ticket t = new Ticket();
                t.setOrder(order);
                t.setEventTicket(ticket);
                t.setEvent(order.getEvent());
                t.setNumero(References.unique("TCK", 10, ticketEntityRepository::existsByNumero));
                t.setParticipantNom(order.getAcheteurNom());
                t.setParticipantEmail(order.getAcheteurEmail());
                t.setStatut(TicketStatus.EMISE);
                t = ticketEntityRepository.save(t);
                qrCodeService.issueFor(t);
            }
        }
        order.setStatut(TicketOrderStatus.PAYEE);
        order.setPayeLe(Instant.now());
        auditService.record(order.getUser().getId(), order.getUser().getEmail(),
                "TICKET_ORDER_PAID", "TicketOrder", order.getId().toString(), null,
                "ref=" + order.getReference());
        eventPublisher.publishEvent(
                new PaymentSucceededEvent(PaymentSucceededEvent.TICKET_ORDER, order.getId()));
    }

    private void releaseReservations(TicketOrder order) {
        for (TicketOrderLine line : order.getLines()) {
            EventTicket ticket = ticketRepository.findByIdForUpdate(line.getEventTicket().getId())
                    .orElseThrow();
            ticket.setQuantiteReservee(Math.max(0, ticket.getQuantiteReservee() - line.getQuantite()));
        }
    }

    private TicketOrder loadForActor(UUID id) {
        TicketOrder order = orderRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Commande", id));
        UUID me = currentUser.requireId();
        boolean buyer = order.getUser().getId().equals(me);
        boolean organiser = order.getEvent().isOwnedBy(me);
        boolean admin = currentUser.hasAuthority(bf.evenements.plateforme.rbac.Permissions.PAYMENT_MANAGE);
        if (!buyer && !organiser && !admin) {
            throw new AccessDeniedException("Accès à la commande refusé.");
        }
        return order;
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
