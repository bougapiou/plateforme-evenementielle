import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import '../../core/format.dart';
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

    Uint8List? qr;
    if (kIsWeb) {
      // No filesystem in the browser — fetch each time, no offline cache.
      try {
        qr = Uint8List.fromList(
            await api.bytes('/api/tickets/${widget.ticketId}/qr.png'));
      } catch (_) {
        qr = null;
      }
      return _TicketView(ticket, qr);
    }

    final dir = await getApplicationDocumentsDirectory();
    final file = File('${dir.path}/qr_${widget.ticketId}.png');
    if (await file.exists()) {
      qr = await file.readAsBytes();
    } else {
      try {
        final bytes = await api.bytes('/api/tickets/${widget.ticketId}/qr.png');
        qr = Uint8List.fromList(bytes);
        await file.writeAsBytes(qr, flush: true);
      } catch (_) {
        qr = null;
      }
    }
    return _TicketView(ticket, qr);
  }

  Future<void> _openPdf(Ticket t) async {
    setState(() => _downloading = true);
    try {
      final api = ref.read(apiClientProvider);
      final path = await api.downloadToCache(
        '/api/tickets/${t.id}/pdf',
        'billet-${t.numero}.pdf',
      );
      await OpenFilex.open(path);
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
              if (!kIsWeb) ...[
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
