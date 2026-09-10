package bf.evenements.plateforme.ticket;

import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventActivity;
import bf.evenements.plateforme.event.EventActivityRepository;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.event.EventService;
import bf.evenements.plateforme.ticket.dto.EventTicketRequest;
import bf.evenements.plateforme.ticket.dto.EventTicketResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventTicketService {

    private final EventTicketRepository ticketRepository;
    private final EventActivityRepository activityRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public List<EventTicketResponse> listForManagement(UUID eventId) {
        eventService.loadManaged(eventId);
        return ticketRepository.findByEventIdOrderByOrdreAscPrixMontantAsc(eventId).stream()
                .map(EventTicketResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventTicketResponse> listPublic(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Événement", eventId));
        if (!event.getStatut().isPubliclyVisible()) {
            throw ResourceNotFoundException.of("Événement", eventId);
        }
        return ticketRepository.findByEventIdOrderByOrdreAscPrixMontantAsc(eventId).stream()
                .filter(EventTicket::isActif)
                .map(EventTicketResponse::publicView)
                .toList();
    }

    @Transactional
    public EventTicketResponse create(UUID eventId, EventTicketRequest request) {
        Event event = eventService.loadManaged(eventId);
        EventTicket ticket = new EventTicket();
        ticket.setEvent(event);
        apply(ticket, request, eventId);
        return EventTicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public EventTicketResponse update(UUID eventId, UUID ticketId, EventTicketRequest request) {
        eventService.loadManaged(eventId);
        EventTicket ticket = load(eventId, ticketId);
        int committed = ticket.getQuantiteVendue() + ticket.getQuantiteReservee();
        if (request.quantiteTotale() < committed) {
            throw new BusinessException("QUOTA_BELOW_COMMITTED",
                    "Le quota ne peut pas être inférieur aux " + committed + " billets déjà engagés.");
        }
        apply(ticket, request, eventId);
        return EventTicketResponse.from(ticket);
    }

    @Transactional
    public void delete(UUID eventId, UUID ticketId) {
        eventService.loadManaged(eventId);
        EventTicket ticket = load(eventId, ticketId);
        if (ticket.getQuantiteVendue() > 0 || ticket.getQuantiteReservee() > 0) {
            throw new BusinessException("TICKET_IN_USE",
                    "Cette catégorie a des billets vendus ou réservés ; désactivez-la plutôt.");
        }
        ticketRepository.delete(ticket);
    }

    private EventTicket load(UUID eventId, UUID ticketId) {
        EventTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ResourceNotFoundException.of("Catégorie de ticket", ticketId));
        if (!ticket.getEvent().getId().equals(eventId)) {
            throw ResourceNotFoundException.of("Catégorie de ticket", ticketId);
        }
        return ticket;
    }

    private void apply(EventTicket t, EventTicketRequest r, UUID eventId) {
        t.setNom(r.nom().trim());
        t.setDescription(r.description());
        t.setPrixMontant(r.prixMontant());
        t.setDevise(r.devise() == null || r.devise().isBlank()
                ? Money.DEFAULT_CURRENCY : r.devise().toUpperCase());
        t.setPortee(r.portee());
        t.setQuantiteTotale(r.quantiteTotale());
        if (r.limiteParUtilisateur() != null) {
            t.setLimiteParUtilisateur(r.limiteParUtilisateur());
        }
        if (t.getPrixMontant().signum() == 0) {
            t.setLimiteParUtilisateur(1); // one free ticket per person
        }
        t.setVenteDebut(r.venteDebut());
        t.setVenteFin(r.venteFin());
        if (r.actif() != null) {
            t.setActif(r.actif());
        }
        if (r.ordre() != null) {
            t.setOrdre(r.ordre());
        }
        t.getActivities().clear();
        if (r.portee() == TicketScope.ACTIVITE) {
            Set<UUID> ids = r.activityIds() == null ? Set.of() : r.activityIds();
            if (ids.isEmpty()) {
                throw new BusinessException("ACTIVITIES_REQUIRED",
                        "Un ticket de portée ACTIVITE doit référencer au moins une activité.");
            }
            Set<EventActivity> activities = new LinkedHashSet<>();
            for (UUID id : ids) {
                EventActivity activity = activityRepository.findById(id)
                        .filter(a -> a.getEvent().getId().equals(eventId))
                        .orElseThrow(() -> ResourceNotFoundException.of("Activité", id));
                activities.add(activity);
            }
            t.getActivities().addAll(activities);
        }
    }
}
