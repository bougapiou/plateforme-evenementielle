package bf.evenements.plateforme.audit;

import bf.evenements.plateforme.audit.AuditService.AuditLogResponse;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Journaux d'activite")
@PreAuthorize("hasAuthority('" + Permissions.AUDIT_READ + "')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Consulter les journaux d'activite (admin)")
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) String action,
                                               @RequestParam(required = false) UUID actorId,
                                               @PageableDefault(size = 30, sort = "createdAt",
                                                       direction = Sort.Direction.DESC)
                                               Pageable pageable) {
        return auditService.search(action, actorId, pageable);
    }
}
