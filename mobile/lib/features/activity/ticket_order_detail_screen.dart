import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class TicketOrderDetailScreen extends ConsumerStatefulWidget {
  final String orderId;
  const TicketOrderDetailScreen({super.key, required this.orderId});

  @override
  ConsumerState<TicketOrderDetailScreen> createState() =>
      _TicketOrderDetailScreenState();
}

class _TicketOrderDetailScreenState
    extends ConsumerState<TicketOrderDetailScreen> {
  late Future<TicketOrder> _future;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _future = ref.read(ticketsRepositoryProvider).order(widget.orderId);
  }

  void _refresh() => setState(
      () => _future = ref.read(ticketsRepositoryProvider).order(widget.orderId));

  Future<void> _cancel() async {
    setState(() => _busy = true);
    try {
      await ref.read(ticketsRepositoryProvider).cancelOrder(widget.orderId);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Commande de billets')),
      body: FutureView<TicketOrder>(
        future: _future,
        onRetry: _refresh,
        builder: (o) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(children: [
              Expanded(
                child: Text(o.eventNom,
                    style: Theme.of(context).textTheme.titleLarge),
              ),
              StatusChip(o.statut),
            ]),
            const SizedBox(height: 4),
            Text('Réf. ${o.reference}',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 16),
            Card(
              child: Column(
                children: [
                  ...o.lignes.map((l) => ListTile(
                        dense: true,
                        title: Text(l.ticketNom),
                        trailing: Text('${l.quantite} × ${Fmt.money(l.prixUnitaire)}'),
                      )),
                  const Divider(height: 1),
                  ListTile(
                    title: const Text('Total'),
                    trailing: Text(o.montantFormatte,
                        style: const TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            if (o.expireLe != null && o.enAttente)
              Text('À régler avant le ${Fmt.dateTime(o.expireLe)}',
                  style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 20),
            if (o.enAttente) ...[
              FilledButton.icon(
                onPressed: _busy
                    ? null
                    : () async {
                        await context.push('/paiement/TICKET_ORDER/${o.id}');
                        _refresh();
                      },
                icon: const Icon(Icons.payment),
                label: const Text('Payer'),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: _busy ? null : _cancel,
                child: const Text('Annuler la commande'),
              ),
            ],
            if (o.payee)
              OutlinedButton.icon(
                onPressed: () => context.go('/billets'),
                icon: const Icon(Icons.confirmation_number_outlined),
                label: const Text('Voir mes billets'),
              ),
          ],
        ),
      ),
    );
  }
}
