import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:open_filex/open_filex.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class StandReservationDetailScreen extends ConsumerStatefulWidget {
  final String reservationId;
  const StandReservationDetailScreen({super.key, required this.reservationId});

  @override
  ConsumerState<StandReservationDetailScreen> createState() =>
      _StandReservationDetailScreenState();
}

class _StandReservationDetailScreenState
    extends ConsumerState<StandReservationDetailScreen> {
  late Future<StandReservation> _future;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _future = ref.read(standsRepositoryProvider).get(widget.reservationId);
  }

  void _refresh() => setState(() =>
      _future = ref.read(standsRepositoryProvider).get(widget.reservationId));

  Future<void> _cancel() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        content: const Text('Annuler cette réservation de stand ?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Non')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Oui')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref.read(standsRepositoryProvider).cancel(widget.reservationId);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirmationPdf() async {
    setState(() => _busy = true);
    try {
      final path = await ref.read(apiClientProvider).downloadToCache(
            '/api/stand-reservations/${widget.reservationId}/confirmation.pdf',
            'reservation-${widget.reservationId}.pdf',
          );
      final res = await OpenFilex.open(path);
      if (res.type != ResultType.done && mounted) {
        showSnack(context, 'PDF enregistré ; aucune application pour l\'ouvrir.',
            error: true);
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Téléchargement impossible : $e', error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Réservation de stand')),
      body: FutureView<StandReservation>(
        future: _future,
        onRetry: _refresh,
        builder: (r) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(children: [
              Expanded(
                child: Text(r.eventNom,
                    style: Theme.of(context).textTheme.titleLarge),
              ),
              StatusChip(r.statut),
            ]),
            const SizedBox(height: 4),
            Text('Réf. ${r.reference}',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 16),
            _row('Stand', '${r.standNumero} — ${r.standTypeNom}'),
            _row('Montant', r.montantFormatte),
            if (r.holdExpireLe != null && r.aPayer)
              _row('Blocage jusqu\'à', Fmt.dateTime(r.holdExpireLe)),
            _row('Créée le', Fmt.dateTime(r.createdAt)),
            const SizedBox(height: 24),
            if (r.aPayer) ...[
              FilledButton.icon(
                onPressed: _busy
                    ? null
                    : () async {
                        await context
                            .push('/paiement/STAND_RESERVATION/${r.id}');
                        _refresh();
                      },
                icon: const Icon(Icons.payment),
                label: const Text('Payer'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: _busy ? null : _cancel,
                child: const Text('Annuler la réservation'),
              ),
            ],
            if (r.statut == 'PAYE' || r.statut == 'CONFIRME')
              OutlinedButton.icon(
                onPressed: _busy ? null : _confirmationPdf,
                icon: const Icon(Icons.picture_as_pdf_outlined),
                label: const Text('Confirmation (PDF)'),
              ),
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(
              width: 120,
              child: Text(label,
                  style: const TextStyle(color: Color(0xFF64748B)))),
          Expanded(child: Text(value)),
        ]),
      );
}
