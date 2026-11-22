package bf.evenements.plateforme.report;

import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.report.ParticipantListService.Criteria;
import bf.evenements.plateforme.report.ParticipantListService.Format;
import bf.evenements.plateforme.report.ParticipantListService.Perimetre;
import bf.evenements.plateforme.report.ParticipantListService.Source;
import bf.evenements.plateforme.report.ReportService.Granularite;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reporting on one event: day / whole-event figures, sensor flow, participant lists. */
@RestController
@RequestMapping("/api/reports/events/{eventId}")
@RequiredArgsConstructor
@Tag(name = "Rapports")
@PreAuthorize("hasAuthority('" + Permissions.STATS_GLOBAL_READ + "') or hasAuthority('"
        + Permissions.STATS_OWN_READ + "')")
public class ReportController {

    private final ReportService reportService;
    private final ParticipantListService participantListService;
    private final BilanPdfService bilanPdfService;

    @GetMapping("/stats")
    @Operation(summary = "Statistiques d'une journée (jour=AAAA-MM-JJ) ou de tout l'événement (sans jour)")
    public ReportService.EventReport stats(
            @PathVariable UUID eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        return reportService.eventReport(eventId, jour);
    }

    @GetMapping("/stats/activites/export")
    @Operation(summary = "Exporter le tableau « Par activité » en CSV ou Excel (format=CSV|XLSX)")
    public ResponseEntity<byte[]> exportActivities(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "XLSX") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        var file = reportService.activityTable(eventId, jour, !"CSV".equalsIgnoreCase(format));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @GetMapping(value = "/bilan.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Rapport de bilan PDF (chiffres clés, top 5 des sessions, fréquentation par jour)")
    public ResponseEntity<byte[]> bilan(@PathVariable UUID eventId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("bilan-evenement.pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bilanPdfService.bilan(eventId));
    }

    @GetMapping("/flux")
    @Operation(summary = "Flux des capteurs laser par jour, ou par heure d'une journée")
    public ReportService.FluxReport flux(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "JOUR") Granularite granularite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
            @RequestParam(required = false) UUID capteurId) {
        return reportService.flux(eventId, granularite, jour, capteurId);
    }

    @GetMapping("/participants")
    @Operation(summary = "Aperçu d'une liste de participants (50 premières lignes + comptes de doublons)")
    public ParticipantListService.Preview participants(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "EVENEMENT") Perimetre perimetre,
            @RequestParam(required = false) UUID cibleId,
            @RequestParam(defaultValue = "BILLETS") Source source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime au,
            @RequestParam(defaultValue = "true") boolean doublons) {
        return participantListService.preview(eventId,
                new Criteria(perimetre, cibleId, source, du, au, doublons));
    }

    @GetMapping("/participants/export")
    @Operation(summary = "Exporter une liste de participants en Excel, Word ou PDF")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID eventId,
            @RequestParam Format format,
            @RequestParam(defaultValue = "EVENEMENT") Perimetre perimetre,
            @RequestParam(required = false) UUID cibleId,
            @RequestParam(defaultValue = "BILLETS") Source source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime au,
            @RequestParam(defaultValue = "true") boolean doublons) {
        var file = participantListService.export(eventId,
                new Criteria(perimetre, cibleId, source, du, au, doublons), format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
