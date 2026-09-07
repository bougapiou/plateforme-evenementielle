package bf.evenements.plateforme.invoice;

import bf.evenements.plateforme.common.events.PaymentSucceededEvent;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.common.pdf.SimplePdf;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.payment.Payment;
import bf.evenements.plateforme.payment.PaymentRepository;
import bf.evenements.plateforme.payment.PaymentStatus;
import bf.evenements.plateforme.payment.PaymentTargetType;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.stand.StandReservationRepository;
import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.ticket.TicketOrderRepository;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(ZoneId.of("Africa/Ouagadougou"));

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final StandReservationRepository standReservationRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;

    @EventListener
    @Transactional
    public void onPayment(PaymentSucceededEvent event) {
        if (event.paymentId() == null || invoiceRepository.existsByPaymentId(event.paymentId())) {
            return;
        }
        Payment payment = paymentRepository.findById(event.paymentId()).orElse(null);
        if (payment == null || payment.getStatut() != PaymentStatus.REUSSI
                || payment.getMontant().signum() <= 0) {
            return;
        }
        User user = userRepository.findById(payment.getUserId()).orElse(null);
        String description = describe(payment);
        Structure structure = resolveStructure(payment);

        Invoice facture = build(Invoice.InvoiceType.FACTURE, payment, user, structure, description);
        Invoice recu = build(Invoice.InvoiceType.RECU, payment, user, structure, description);
        invoiceRepository.save(facture);
        invoiceRepository.save(recu);
        log.info("Facture {} et reçu {} émis pour le paiement {}",
                facture.getNumero(), recu.getNumero(), payment.getReference());
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> myInvoices() {
        return invoiceRepository.findByUserIdOrderByEmiseLeDesc(currentUser.requireId()).stream()
                .map(InvoiceView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> forPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Paiement", paymentId));
        assertAccess(payment.getUserId());
        return invoiceRepository.findByPaymentId(paymentId).stream().map(InvoiceView::from).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceView get(UUID id) {
        Invoice invoice = load(id);
        assertAccess(invoice.getUserId());
        return InvoiceView.from(invoice);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID id) {
        Invoice invoice = load(id);
        assertAccess(invoice.getUserId());
        String label = invoice.getType() == Invoice.InvoiceType.FACTURE ? "FACTURE" : "REÇU DE PAIEMENT";
        return SimplePdf.create(label + "  N° " + invoice.getNumero())
                .text("Plateforme Nationale de Gestion des Événements")
                .text("Émis le " + DATE.format(invoice.getEmiseLe()))
                .spacer()
                .heading("Client")
                .text(invoice.getClientNom() == null ? "-" : invoice.getClientNom())
                .text(invoice.getClientDetails() == null ? "" : invoice.getClientDetails())
                .spacer()
                .heading("Détail")
                .text(invoice.getLignes() == null ? "-" : invoice.getLignes())
                .spacer()
                .heading("Montant")
                .text("Total : " + Money.of(invoice.getMontant(), invoice.getDevise()).formatted())
                .text("TVA : non applicable")
                .spacer()
                .text(invoice.getType() == Invoice.InvoiceType.RECU
                        ? "Paiement reçu et confirmé."
                        : "Facture acquittée.")
                .build();
    }

    // --- helpers ---

    private Invoice build(Invoice.InvoiceType type, Payment payment, User user, Structure structure,
                          String description) {
        Invoice invoice = new Invoice();
        invoice.setType(type);
        invoice.setNumero(nextNumero(type));
        invoice.setPaymentId(payment.getId());
        invoice.setUserId(payment.getUserId());
        invoice.setEventId(payment.getEventId());
        invoice.setMontant(payment.getMontant());
        invoice.setDevise(payment.getDevise());
        invoice.setClientNom(structure != null ? structure.getRaisonSociale()
                : (user != null ? user.getFullName() : "Client"));
        if (structure != null) {
            invoice.setClientDetails(String.join(" · ",
                    structure.getRccm() != null ? "RCCM " + structure.getRccm() : "",
                    structure.getIfu() != null ? "IFU " + structure.getIfu() : "",
                    structure.getVille() != null ? structure.getVille() : "").replaceAll("( ·)+$", ""));
        } else if (user != null) {
            invoice.setClientDetails(user.getEmail());
        }
        invoice.setLignes(description);
        invoice.setEmiseLe(Instant.now());
        return invoice;
    }

    private String nextNumero(Invoice.InvoiceType type) {
        String prefix = type == Invoice.InvoiceType.FACTURE ? "FAC" : "REC";
        int year = java.time.Year.now().getValue();
        long seq = invoiceRepository.countByType(type) + 1;
        String candidate;
        do {
            candidate = String.format("%s-%d-%05d", prefix, year, seq++);
        } while (invoiceRepository.existsByNumero(candidate));
        return candidate;
    }

    private String describe(Payment payment) {
        if (payment.getTargetType() == PaymentTargetType.TICKET_ORDER) {
            return ticketOrderRepository.findById(payment.getTargetId())
                    .map(o -> "Billets — " + o.getEvent().getNom() + " (commande " + o.getReference()
                            + ", " + o.totalQuantity() + " billet(s))")
                    .orElse("Commande de billets");
        }
        return standReservationRepository.findById(payment.getTargetId())
                .map(r -> "Stand " + r.getStand().getNumero() + " (" + r.getStandType().getNom()
                        + ") — " + r.getEvent().getNom() + ", réservation " + r.getNumeroReservation())
                .orElse("Réservation de stand");
    }

    private Structure resolveStructure(Payment payment) {
        if (payment.getTargetType() == PaymentTargetType.TICKET_ORDER) {
            return ticketOrderRepository.findById(payment.getTargetId())
                    .map(bf.evenements.plateforme.ticket.TicketOrder::getStructure).orElse(null);
        }
        return standReservationRepository.findById(payment.getTargetId())
                .map(bf.evenements.plateforme.stand.StandReservation::getStructure).orElse(null);
    }

    private Invoice load(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Facture", id));
    }

    private void assertAccess(UUID ownerId) {
        if (!ownerId.equals(currentUser.requireId())
                && !currentUser.hasAuthority(Permissions.PAYMENT_READ)) {
            throw new AccessDeniedException("Accès au document refusé.");
        }
    }

    public record InvoiceView(UUID id, String numero, String type, UUID paymentId,
                              java.math.BigDecimal montant, String devise, String montantFormatte,
                              String clientNom, String lignes, Instant emiseLe, String pdfUrl) {
        static InvoiceView from(Invoice i) {
            return new InvoiceView(i.getId(), i.getNumero(), i.getType().name(), i.getPaymentId(),
                    i.getMontant(), i.getDevise(),
                    Money.of(i.getMontant(), i.getDevise()).formatted(),
                    i.getClientNom(), i.getLignes(), i.getEmiseLe(),
                    "/api/invoices/" + i.getId() + "/pdf");
        }
    }
}
