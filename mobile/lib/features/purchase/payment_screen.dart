import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../core/documents.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

/// Payment step for a ticket order (TICKET_ORDER) or a stand reservation
/// (STAND_RESERVATION).
///
/// With the sandbox provider (dev/démo) : initiate → simulate callback,
/// instant result. With a real provider (ex. FasoArzeka) : initiate → open
/// its hosted checkout in an in-app browser → once closed, re-check the
/// payment (the real confirmation always comes from the provider, not from
/// the app closing the browser).
class PaymentScreen extends ConsumerStatefulWidget {
  final String targetType;
  final String targetId;
  const PaymentScreen({
    super.key,
    required this.targetType,
    required this.targetId,
  });

  @override
  ConsumerState<PaymentScreen> createState() => _PaymentScreenState();
}

const _methods = <String, String>{
  'MOBILE_MONEY_ORANGE': 'Orange Money',
  'MOBILE_MONEY_MOOV': 'Moov Money',
  'MOBILE_MONEY_TELECEL': 'Telecel Money',
  'FASO_ARZEKA': 'FasoArzeka',
  'CARTE_BANCAIRE': 'Carte bancaire',
};

class _PaymentScreenState extends ConsumerState<PaymentScreen> {
  late Future<_Target> _future;
  String _method = 'MOBILE_MONEY_ORANGE';
  bool _processing = false;
  String? _error;

  bool get _isOrder => widget.targetType == 'TICKET_ORDER';

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_Target> _load() async {
    if (_isOrder) {
      final o = await ref.read(ticketsRepositoryProvider).order(widget.targetId);
      return _Target(
        title: o.eventNom,
        reference: o.reference,
        amount: o.montantTotal,
        devise: o.devise,
        statutPaye: o.payee,
        lines: o.lignes
            .map((l) => '${l.quantite} × ${l.ticketNom}')
            .toList(),
      );
    }
    final r = await ref.read(standsRepositoryProvider).get(widget.targetId);
    return _Target(
      title: r.eventNom,
      reference: r.reference,
      amount: r.montant,
      devise: 'XOF',
      statutPaye: r.statut == 'PAYE' || r.statut == 'CONFIRME',
      lines: ['Stand ${r.standNumero} — ${r.standTypeNom}'],
    );
  }

  Future<void> _pay(String outcome, _Target target) async {
    setState(() {
      _processing = true;
      _error = null;
    });
    try {
      final payments = ref.read(paymentsRepositoryProvider);
      final payment = await payments.initiate(
        targetType: widget.targetType,
        targetId: widget.targetId,
        moyen: _method,
      );
      if (payment.provider == 'sandbox') {
        final result =
            await payments.simulate(payment.reference, outcome: outcome);
        if (!mounted) return;
        if (result.reussi) {
          _showSuccess(target);
        } else {
          setState(() => _error =
              'Le paiement a échoué (${statutLabel(result.statut)}). Vous pouvez réessayer.');
        }
        return;
      }
      await _payWithRealProvider(payment, target);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _processing = false);
    }
  }

  /// Opens the provider's own hosted checkout page. It stays an in-app
  /// browser (not a full app-switch) so closing it — whether the payer
  /// finished paying or gave up — naturally hands control back here.
  Future<void> _payWithRealProvider(Payment payment, _Target target) async {
    final uri = Uri.tryParse(payment.paymentUrl ?? '');
    if (uri == null) {
      if (mounted) setState(() => _error = 'Lien de paiement invalide.');
      return;
    }
    final opened = await launchUrl(uri, mode: LaunchMode.inAppBrowserView);
    if (!opened) {
      if (mounted) setState(() => _error = 'Impossible d\'ouvrir la page de paiement.');
      return;
    }
    if (!mounted) return;
    // The payer closed the checkout page — ask the provider what really
    // happened rather than assume anything from that alone.
    try {
      final result =
          await ref.read(paymentsRepositoryProvider).recheck(payment.reference);
      if (!mounted) return;
      if (result.reussi) {
        _showSuccess(target);
      } else if (result.statut == 'EN_ATTENTE') {
        _showPending(payment.reference);
      } else {
        setState(() => _error =
            'Paiement non confirmé (${statutLabel(result.statut)}). Vous pouvez réessayer.');
      }
    } on ApiException {
      if (mounted) _showPending(payment.reference);
    }
  }

  void _showPending(String reference) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        icon: const Icon(Icons.hourglass_top, color: Color(0xFFB45309), size: 40),
        title: const Text('Confirmation en attente'),
        content: Text(
          'Nous n\'avons pas encore reçu la confirmation du paiement $reference. '
          'Si vous avez bien payé, elle arrive généralement en quelques instants — '
          'vous pouvez suivre son statut dans « Mes paiements ».',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Fermer'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(context).pop();
              context.go(_isOrder ? '/billets' : '/activite');
            },
            child: Text(_isOrder ? 'Voir mes billets' : 'Voir mes réservations'),
          ),
        ],
      ),
    );
  }

  /// Best-effort: fetch the tickets this order produced and download each
  /// PDF right away, so the payer doesn't have to go find them afterwards.
  Future<void> _autoDownloadTickets(String orderReference) async {
    try {
      final all = await ref.read(ticketsRepositoryProvider).myTickets();
      final mine = all.where((t) => t.orderReference == orderReference);
      final api = ref.read(apiClientProvider);
      for (final t in mine) {
        await fetchAndPresentDocument(api,
            path: '/api/tickets/${t.id}/pdf',
            filename: 'billet-${t.numero}.pdf');
      }
    } catch (_) {
      // best-effort — the ticket stays available in « Mes billets » regardless
    }
  }

  void _showSuccess(_Target target) {
    if (_isOrder) _autoDownloadTickets(target.reference);
    final isGuest = ref.read(isGuestProvider);
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        icon: const Icon(Icons.check_circle, color: Color(0xFF16A34A), size: 48),
        title: const Text('Paiement confirmé'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_isOrder
                ? 'Vos billets électroniques sont disponibles dans « Mes billets » et envoyés par e-mail.'
                : 'Votre réservation de stand est confirmée.'),
            if (isGuest) ...[
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  'Créez un compte (choix d\'un mot de passe) pour retrouver vos '
                  'billets et vos factures sur tous vos appareils.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
            ],
          ],
        ),
        actions: [
          if (isGuest)
            FilledButton.icon(
              onPressed: () {
                Navigator.of(context).pop();
                context.push('/finaliser-compte');
              },
              icon: const Icon(Icons.person_add_alt),
              label: const Text('Créer un compte'),
            ),
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              context.go(_isOrder ? '/billets' : '/activite');
            },
            child: Text(isGuest
                ? 'Plus tard'
                : (_isOrder ? 'Voir mes billets' : 'Voir mes réservations')),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Paiement')),
      body: FutureView<_Target>(
        future: _future,
        onRetry: () => setState(() => _future = _load()),
        builder: (t) {
          if (t.statutPaye) {
            return const EmptyState(
              icon: Icons.verified,
              title: 'Déjà payé',
              subtitle: 'Ce paiement a déjà été confirmé.',
            );
          }
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(t.title,
                          style: Theme.of(context).textTheme.titleMedium),
                      Text('Réf. ${t.reference}',
                          style: Theme.of(context).textTheme.bodySmall),
                      const Divider(height: 20),
                      ...t.lines.map((l) => Padding(
                            padding: const EdgeInsets.only(bottom: 4),
                            child: Text(l),
                          )),
                      const Divider(height: 20),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Total à payer'),
                          Text(Fmt.money(t.amount, t.devise),
                              style: Theme.of(context).textTheme.titleLarge),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Text('Moyen de paiement',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 4),
              ..._methods.entries.map((e) => RadioListTile<String>(
                    value: e.key,
                    groupValue: _method,
                    onChanged: _processing
                        ? null
                        : (v) => setState(() => _method = v!),
                    title: Text(e.value),
                    contentPadding: EdgeInsets.zero,
                  )),
              const SizedBox(height: 8),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(_error!,
                      style: const TextStyle(color: Color(0xFF991B1B))),
                ),
              FilledButton.icon(
                onPressed: _processing ? null : () => _pay('SUCCESS', t),
                icon: _processing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.lock_outline),
                label: Text(_processing
                    ? 'Traitement du paiement…'
                    : 'Payer ${Fmt.money(t.amount, t.devise)}'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: _processing ? null : () => _pay('FAILURE', t),
                child: const Text('Simuler un paiement échoué'),
              ),
              const SizedBox(height: 4),
              Text(
                'Environnement de démonstration : le paiement est simulé via le '
                'fournisseur sandbox. Rien n\'est validé tant que le paiement '
                'n\'est pas confirmé.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          );
        },
      ),
    );
  }
}

class _Target {
  final String title;
  final String reference;
  final num amount;
  final String devise;
  final bool statutPaye;
  final List<String> lines;
  _Target({
    required this.title,
    required this.reference,
    required this.amount,
    required this.devise,
    required this.statutPaye,
    required this.lines,
  });
}
