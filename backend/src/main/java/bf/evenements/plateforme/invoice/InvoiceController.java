package bf.evenements.plateforme.invoice;

import bf.evenements.plateforme.invoice.InvoiceService.InvoiceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Factures & reçus")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/invoices/my")
    @Operation(summary = "Mes factures et reçus")
    public List<InvoiceView> mine() {
        return invoiceService.myInvoices();
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Détail d'une facture / d'un reçu")
    public InvoiceView get(@PathVariable UUID id) {
        return invoiceService.get(id);
    }

    @GetMapping("/payments/{paymentId}/invoices")
    @Operation(summary = "Documents liés à un paiement")
    public List<InvoiceView> forPayment(@PathVariable UUID paymentId) {
        return invoiceService.forPayment(paymentId);
    }

    @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Télécharger le PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename("document-" + id + ".pdf").build().toString())
                .body(invoiceService.pdf(id));
    }
}
