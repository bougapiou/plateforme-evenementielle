package bf.evenements.plateforme.standvisit;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventRepository;
import bf.evenements.plateforme.stand.Stand;
import bf.evenements.plateforme.stand.StandRepository;
import bf.evenements.plateforme.stand.StandReservation;
import bf.evenements.plateforme.stand.StandReservationRepository;
import bf.evenements.plateforme.standvisit.dto.FrequentationResponse;
import bf.evenements.plateforme.standvisit.dto.SignalerPassageRequest;
import bf.evenements.plateforme.standvisit.dto.SignalerPassagesRequest;
import bf.evenements.plateforme.standvisit.dto.StandFrequentationResponse;
import bf.evenements.plateforme.standvisit.dto.StandInfoResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fréquentation des stands, indépendante du contrôle d'accès billetterie et des capteurs laser :
 * un visiteur signale lui-même son passage depuis une page propre à un stand (un lien/QR par
 * stand, imprimé ou affiché sur place). Aucun compte, aucune identité obligatoire. Tout est public
 * ici — la page de statistiques est un classement en direct que tout le monde peut ouvrir, par
 * choix de conception (voir la décision produit du projet).
 */
@Service
@RequiredArgsConstructor
public class VisiteStandService {

    private final VisiteStandRepository visiteRepository;
    private final StandRepository standRepository;
    private final StandReservationRepository reservationRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public StandInfoResponse standInfo(String slug, UUID standId) {
        Stand stand = resolveStand(slug, standId);
        return StandInfoResponse.from(stand, exposantNom(stand.getId()));
    }

    @Transactional
    public void signaler(String slug, UUID standId, SignalerPassageRequest request) {
        Stand stand = resolveStand(slug, standId);
        VisiteStand v = new VisiteStand();
        v.setEvent(stand.getEvent());
        v.setStand(stand);
        v.setNom(blankToNull(request.nom()));
        v.setPrenom(blankToNull(request.prenom()));
        v.setTelephone(blankToNull(request.telephone()));
        visiteRepository.save(v);
    }

    /** Le visiteur coche en une fois tous les stands visités, puis donne une identité (facultative) une seule fois. */
    @Transactional
    public int signalerPlusieurs(String slug, SignalerPassagesRequest request) {
        Event event = resolveEvent(slug);
        List<Stand> stands = standRepository.findAllById(request.standIds()).stream()
                .filter(s -> s.getEvent().getId().equals(event.getId()))
                .toList();
        if (stands.isEmpty()) {
            throw new ResourceNotFoundException("Aucun stand valide pour cet événement");
        }
        String nom = blankToNull(request.nom());
        String prenom = blankToNull(request.prenom());
        String telephone = blankToNull(request.telephone());
        List<VisiteStand> visites = stands.stream().map(s -> {
            VisiteStand v = new VisiteStand();
            v.setEvent(event);
            v.setStand(s);
            v.setNom(nom);
            v.setPrenom(prenom);
            v.setTelephone(telephone);
            return v;
        }).toList();
        visiteRepository.saveAll(visites);
        return visites.size();
    }

    @Transactional(readOnly = true)
    public FrequentationResponse stats(String slug) {
        Event event = resolveEvent(slug);
        Map<UUID, VisiteStandRepository.StandCountDetail> parStand = visiteRepository
                .countDetailByStandForEvent(event.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        VisiteStandRepository.StandCountDetail::getStandId, d -> d));

        List<StandFrequentationResponse> stands = standRepository
                .findByEventIdOrderByNumeroAsc(event.getId()).stream()
                .map(s -> {
                    VisiteStandRepository.StandCountDetail d = parStand.get(s.getId());
                    long identifiees = d == null ? 0L : d.getIdentifiees();
                    long anonymes = d == null ? 0L : d.getAnonymes();
                    return new StandFrequentationResponse(s.getId(), s.getNumero(), s.getStandType().getNom(),
                            exposantNom(s.getId()), identifiees + anonymes, identifiees, anonymes);
                })
                .sorted(Comparator.comparingLong(StandFrequentationResponse::visites).reversed())
                .toList();

        long totalIdentifiees = stands.stream().mapToLong(StandFrequentationResponse::visitesIdentifiees).sum();
        long totalAnonymes = stands.stream().mapToLong(StandFrequentationResponse::visitesAnonymes).sum();
        return new FrequentationResponse(event.getNom(), totalIdentifiees + totalAnonymes,
                totalIdentifiees, totalAnonymes, stands);
    }

    @Transactional(readOnly = true)
    public StandFrequentationResponse standStats(String slug, UUID standId) {
        Stand stand = resolveStand(slug, standId);
        VisiteStandRepository.StandCountDetail d = visiteRepository.countDetailForStand(standId);
        long identifiees = d == null || d.getIdentifiees() == null ? 0L : d.getIdentifiees();
        long anonymes = d == null || d.getAnonymes() == null ? 0L : d.getAnonymes();
        return new StandFrequentationResponse(stand.getId(), stand.getNumero(), stand.getStandType().getNom(),
                exposantNom(stand.getId()), identifiees + anonymes, identifiees, anonymes);
    }

    /** Nom de la structure ou de l'utilisateur derrière une réservation active de ce stand, s'il y en a. */
    private String exposantNom(UUID standId) {
        List<StandReservation> actives = reservationRepository.findActiveByStand(standId);
        if (actives.isEmpty()) {
            return null;
        }
        StandReservation r = actives.get(0);
        return r.getStructure() != null ? r.getStructure().getRaisonSociale() : r.getUser().getFullName();
    }

    private Stand resolveStand(String slug, UUID standId) {
        Event event = resolveEvent(slug);
        Stand stand = standRepository.findById(standId)
                .orElseThrow(() -> ResourceNotFoundException.of("Stand", standId));
        if (!stand.getEvent().getId().equals(event.getId())) {
            throw new ResourceNotFoundException("Stand introuvable pour cet événement");
        }
        return stand;
    }

    private Event resolveEvent(String slug) {
        return eventRepository.findBySlug(slug)
                .filter(e -> e.getStatut().isPubliclyVisible())
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable : " + slug));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
