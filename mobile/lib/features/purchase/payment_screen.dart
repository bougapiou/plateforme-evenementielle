import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

/// Payment step for a ticket order (TICKET_ORDER) or a stand reservation
/// (STAND_RESERVATION). Uses the sandbox provider: initiate → simulate callback.
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

  Future<void> _pay(String outcome) async {
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
      final result =
          await payments.simulate(payment.reference, outcome: outcome);
      if (!mounted) return;
      if (result.reussi) {
        _showSuccess();
      } else {
        setState(() => _error =
            'Le paiement a échoué (${statutLabel(result.statut)}). Vous pouvez réessayer.');
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _processing = false);
    }
  }

  void _showSuccess() {
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
                onPressed: _processing ? null : () => _pay('SUCCESS'),
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
                onPressed: _processing ? null : () => _pay('FAILURE'),
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
