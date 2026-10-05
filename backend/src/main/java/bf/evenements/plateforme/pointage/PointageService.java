package bf.evenements.plateforme.pointage;

import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.pointage.dto.PointageStandResponse;
import bf.evenements.plateforme.pointage.dto.PointageStandStatResponse;
import bf.evenements.plateforme.pointage.dto.PointageStatsResponse;
import bf.evenements.plateforme.pointage.dto.SignalerPointagePassagesRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pointage des visiteurs : module indépendant des événements. Un visiteur coche, parmi tous les
 * stands (points de comptage), ceux qu'il a visités, donne une identité facultative, et des
 * statistiques publiques (globales et par stand) s'actualisent en direct — toujours dans le même
 * ordre d'affichage que la liste des stands, pas un classement qui change de place.
 */
@Service
@RequiredArgsConstructor
public class PointageService {

    private final PointageStandRepository standRepository;
    private final PointagePassageRepository passageRepository;

    @Transactional(readOnly = true)
    public List<PointageStandResponse> listStands() {
        return standRepository.findAllByOrderByOrdreAscNomAsc().stream()
                .map(PointageStandResponse::from)
                .toList();
    }

    @Transactional
    public int signalerPlusieurs(SignalerPointagePassagesRequest request) {
        List<PointageStand> stands = standRepository.findAllById(request.standIds());
        if (stands.isEmpty()) {
            throw new ResourceNotFoundException("Aucun stand valide sélectionné");
        }
        String nom = blankToNull(request.nom());
        String prenom = blankToNull(request.prenom());
        String telephone = blankToNull(request.telephone());
        List<PointagePassage> passages = stands.stream().map(s -> {
            PointagePassage p = new PointagePassage();
            p.setStand(s);
            p.setNom(nom);
            p.setPrenom(prenom);
            p.setTelephone(telephone);
            return p;
        }).toList();
        passageRepository.saveAll(passages);
        return passages.size();
    }

    @Transactional(readOnly = true)
    public PointageStatsResponse stats() {
        Map<UUID, PointagePassageRepository.StandCountDetail> parStand = passageRepository
                .countDetailByStand().stream()
                .collect(java.util.stream.Collectors.toMap(
                        PointagePassageRepository.StandCountDetail::getStandId, d -> d));

        List<PointageStandStatResponse> stands = standRepository.findAllByOrderByOrdreAscNomAsc().stream()
                .map(s -> {
                    PointagePassageRepository.StandCountDetail d = parStand.get(s.getId());
                    long identifiees = d == null ? 0L : d.getIdentifiees();
                    long anonymes = d == null ? 0L : d.getAnonymes();
                    return new PointageStandStatResponse(s.getId(), s.getNom(),
                            identifiees + anonymes, identifiees, anonymes);
                })
                .toList();

        long totalIdentifiees = stands.stream().mapToLong(PointageStandStatResponse::visitesIdentifiees).sum();
        long totalAnonymes = stands.stream().mapToLong(PointageStandStatResponse::visitesAnonymes).sum();
        return new PointageStatsResponse(totalIdentifiees + totalAnonymes, totalIdentifiees, totalAnonymes, stands);
    }

    @Transactional(readOnly = true)
    public PointageStandStatResponse standStats(UUID standId) {
        PointageStand stand = standRepository.findById(standId)
                .orElseThrow(() -> ResourceNotFoundException.of("Stand", standId));
        PointagePassageRepository.StandCountDetail d = passageRepository.countDetailForStand(standId);
        long identifiees = d == null || d.getIdentifiees() == null ? 0L : d.getIdentifiees();
        long anonymes = d == null || d.getAnonymes() == null ? 0L : d.getAnonymes();
        return new PointageStandStatResponse(stand.getId(), stand.getNom(),
                identifiees + anonymes, identifiees, anonymes);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
