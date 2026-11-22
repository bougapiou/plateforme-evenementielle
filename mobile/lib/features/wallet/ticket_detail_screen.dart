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

  Future<void> _voirPdf(Ticket t) async {
    setState(() => _downloading = true);
    try {
      final outcome = await viewDocument(
        ref.read(apiClientProvider),
        path: '/api/tickets/${t.id}/pdf',
        filename: 'billet-${t.numero}.pdf',
      );
      if (mounted && outcome == DocOutcome.savedNoViewer) {
        showSnack(context, 'PDF enregistré, mais aucune application ne peut l\'ouvrir.', error: true);
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Ouverture impossible : $e', error: true);
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
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
              // Couverture de l'événement (générée s'il n'a pas de photo) — toujours
              // au-dessus du QR, jamais par-dessus : le QR reste visible et
              // scannable en toutes circonstances.
              ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: AspectRatio(
                  aspectRatio: 16 / 8,
                  child: EventCover(nom: t.eventNom, url: t.eventCoverUrl),
                ),
              ),
              const SizedBox(height: 16),
              Center(
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2DDD6)),
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
              Center(
                child: Wrap(spacing: 6, children: [
                  _TypeChip(t),
                  StatusChip(t.statut),
                ]),
              ),
              const SizedBox(height: 20),
              Text(t.eventNom, style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 2),
              Text(t.titre, style: Theme.of(context).textTheme.titleLarge),
              if (t.categorieDescription != null && !t.estSession) ...[
                const SizedBox(height: 4),
                Text(t.categorieDescription!, style: Theme.of(context).textTheme.bodyMedium),
              ],
              const SizedBox(height: 12),
              _row('Billet n°', t.numero),
              if (t.participantNom != null)
                _row('Participant', t.participantNom!),
              if (t.estSession) ...[
                _row('Date', Fmt.date(t.sessions.first.dateDebut)),
                _row('Horaire', _heures(t.sessions.first)),
                if (t.sessions.first.salle != null) _row('Salle', t.sessions.first.salle!),
              ] else if (!t.estPass)
                _row('Date', Fmt.dateTime(t.eventDateDebut)),
              if (t.lieu != null) _row('Lieu', t.lieu!),
              if (t.orderReference != null)
                _row('Commande', t.orderReference!),
              if (t.estPass) ...[
                const SizedBox(height: 16),
                Text('SESSIONS INCLUSES',
                    style: Theme.of(context).textTheme.labelSmall),
                const SizedBox(height: 8),
                ...t.sessions.map((s) => Container(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: const BoxDecoration(
                          border: Border(bottom: BorderSide(color: Color(0xFFE2DDD6)))),
                      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        SizedBox(
                          width: 92,
                          child: Text(Fmt.dateShort(s.dateDebut).toUpperCase(),
                              style: const TextStyle(
                                  fontFamily: 'Archivo',
                                  fontWeight: FontWeight.w700,
                                  fontSize: 12,
                                  color: Color(0xFF026C31))),
                        ),
                        Expanded(
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(s.titre, style: const TextStyle(fontWeight: FontWeight.w600)),
                            Text([_heures(s), if (s.salle != null) s.salle!].join(' · '),
                                style: Theme.of(context).textTheme.bodySmall),
                          ]),
                        ),
                      ]),
                    )),
              ],
              const SizedBox(height: 24),
              Row(children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _downloading ? null : () => _voirPdf(t),
                    icon: _downloading
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.visibility_outlined),
                    label: const Text('Voir le billet'),
                  ),
                ),
                if (kIsWeb) ...[
                  const SizedBox(width: 10),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _downloading ? null : () => _openPdf(t),
                      icon: const Icon(Icons.download_outlined),
                      label: const Text('Télécharger'),
                    ),
                  ),
                ],
              ]),
              if (!kIsWeb) ...[
                const SizedBox(height: 6),
                Text('Le PDF s\'ouvre dans la visionneuse du téléphone : vous pouvez l\'enregistrer ou le partager depuis celle-ci.',
                    style: Theme.of(context).textTheme.bodySmall),
              ],
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

  String _heures(TicketSession s) => s.dateFin != null
      ? '${Fmt.time(s.dateDebut)} – ${Fmt.time(s.dateFin)}'
      : Fmt.time(s.dateDebut);

  Widget _row(String label, String value) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
                width: 110,
                child: Text(label,
                    style: const TextStyle(color: Color(0xFF756D66)))),
            Expanded(
                child: Text(value,
                    style: const TextStyle(fontWeight: FontWeight.w500))),
          ],
        ),
      );
}

/// Pastille « Entrée » / « Session » / « Pass · N sessions ».
class _TypeChip extends StatelessWidget {
  final Ticket t;
  const _TypeChip(this.t);

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = t.estPass
        ? (const Color(0xFFFFFCE5), const Color(0xFF7D7100))
        : t.estSession
            ? (const Color(0xFFEEF4F9), const Color(0xFF173F5F))
            : (const Color(0xFFECF7F0), const Color(0xFF045527));
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(999)),
      child: Text(t.typeLabel, style: TextStyle(color: fg, fontSize: 12, fontWeight: FontWeight.w600)),
    );
  }
}

class _TicketView {
  final Ticket ticket;
  final Uint8List? qr;
  _TicketView(this.ticket, this.qr);
}
