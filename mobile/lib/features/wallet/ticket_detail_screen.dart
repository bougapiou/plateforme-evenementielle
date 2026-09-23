import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import '../../core/documents.dart';
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class TicketDetailScreen extends ConsumerStatefulWidget {
  final String ticketId;
  const TicketDetailScreen({super.key, required this.ticketId});

  @override
  ConsumerState<TicketDetailScreen> createState() =>
      _TicketDetailScreenState();
}

class _TicketDetailScreenState extends ConsumerState<TicketDetailScreen> {
  late Future<_TicketView> _future;
  bool _downloading = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_TicketView> _load() async {
    final repo = ref.read(ticketsRepositoryProvider);
    final api = ref.read(apiClientProvider);
    Ticket? ticket;
    try {
      ticket = await repo.ticket(widget.ticketId);
    } catch (_) {
      // offline — fall back to cached list entry if present
      final cached = await repo.myTickets().catchError((_) => <Ticket>[]);
      for (final t in cached) {
        if (t.id == widget.ticketId) {
          ticket = t;
          break;
        }
      }
    }
    if (ticket == null) {
      throw Exception('Billet introuvable et non disponible hors-ligne.');
    }

    final qrPath = '/api/tickets/${widget.ticketId}/qr.png';
    Uint8List? qr;

    if (kIsWeb) {
      try {
        qr = Uint8List.fromList(await api.bytes(qrPath));
      } catch (_) {
        qr = null;
      }
      return _TicketView(ticket, qr);
    }

    final dir = await getApplicationDocumentsDirectory();
    final file = File('${dir.path}/qr_${widget.ticketId}.png');
    if (await file.exists() && await file.length() > 0) {
      qr = await file.readAsBytes();
    } else {
      try {
        final bytes = await api.bytes(qrPath);
        if (bytes.isNotEmpty) {
          qr = Uint8List.fromList(bytes);
          await file.writeAsBytes(qr, flush: true);
        }
      } catch (_) {
        qr = null;
      }
    }
    return _TicketView(ticket, qr);
  }

  Future<void> _openPdf(Ticket t) async {
    setState(() => _downloading = true);
    try {
      final outcome = await fetchAndPresentDocument(
        ref.read(apiClientProvider),
        path: '/api/tickets/${t.id}/pdf',
        filename: 'billet-${t.numero}.pdf',
      );
      if (mounted && outcome == DocOutcome.savedNoViewer) {
        showSnack(context,
            'PDF enregistré, mais aucune application ne peut l\'ouvrir.',
            error: true);
      } else if (mounted && outcome == DocOutcome.downloaded) {
        showSnack(context, 'Téléchargement du billet lancé.');
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Téléchargement impossible : $e', error: true);
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Billet électronique')),
      body: FutureView<_TicketView>(
        future: _future,
        onRetry: () => setState(() => _future = _load()),
        builder: (v) {
          final t = v.ticket;
          return ListView(
            padding: const EdgeInsets.all(20),
            children: [
              // Image de couverture de l'événement, si disponible — toujours
              // au-dessus du QR, jamais par-dessus : le QR reste visible et
              // scannable en toutes circonstances.
              if (t.eventCoverUrl != null) ...[
                ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: AspectRatio(
                    aspectRatio: 16 / 8,
                    child: RemoteImage(url: t.eventCoverUrl),
                  ),
                ),
                const SizedBox(height: 16),
              ],
              Center(
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: v.qr != null
                      ? Image.memory(v.qr!, width: 240, height: 240)
                      : const SizedBox(
                          width: 240,
                          height: 240,
                          child: Center(
                            child: Text('QR indisponible hors-ligne',
                                textAlign: TextAlign.center),
                          ),
                        ),
                ),
              ),
              const SizedBox(height: 12),
              Center(child: StatusChip(t.statut)),
              const SizedBox(height: 20),
              Text(t.eventNom, style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              _row('Billet n°', t.numero),
              if (t.categorieNom != null) _row('Catégorie', t.categorieNom!),
              if (t.participantNom != null)
                _row('Participant', t.participantNom!),
              _row('Date', Fmt.dateTime(t.eventDateDebut)),
              if (t.lieu != null) _row('Lieu', t.lieu!),
              if (t.orderReference != null)
                _row('Commande', t.orderReference!),
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: _downloading ? null : () => _openPdf(t),
                icon: _downloading
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.picture_as_pdf_outlined),
                label: const Text('Télécharger le PDF'),
              ),
              if (!kIsWeb) ...[
                const SizedBox(height: 8),
                Text(
                  'Le QR code est enregistré sur l\'appareil et reste affichable '
                  'sans connexion à l\'entrée de l\'événement.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  Widget _row(String label, String value) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
                width: 110,
                child: Text(label,
                    style: const TextStyle(color: Color(0xFF64748B)))),
            Expanded(
                child: Text(value,
                    style: const TextStyle(fontWeight: FontWeight.w500))),
          ],
        ),
      );
}

class _TicketView {
  final Ticket ticket;
  final Uint8List? qr;
  _TicketView(this.ticket, this.qr);
}
