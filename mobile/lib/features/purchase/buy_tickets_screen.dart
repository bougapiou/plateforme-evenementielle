import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import 'guest_gate.dart';

class BuyTicketsScreen extends ConsumerStatefulWidget {
  final String slug;
  const BuyTicketsScreen({super.key, required this.slug});

  @override
  ConsumerState<BuyTicketsScreen> createState() => _BuyTicketsScreenState();
}

class _BuyTicketsScreenState extends ConsumerState<BuyTicketsScreen> {
  late Future<(EventDetail, List<EventTicketType>)> _future;
  final Map<String, int> _qty = {};
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<(EventDetail, List<EventTicketType>)> _load() async {
    final repo = ref.read(eventsRepositoryProvider);
    final event = await repo.bySlug(widget.slug);
    final tickets = await repo.tickets(widget.slug);
    return (event, tickets.where((t) => t.enVente).toList());
  }

  num _total(List<EventTicketType> tickets) {
    num sum = 0;
    for (final t in tickets) {
      sum += (t.prixMontant) * (_qty[t.id] ?? 0);
    }
    return sum;
  }

  int get _count => _qty.values.fold(0, (a, b) => a + b);

  Future<void> _submit(EventDetail event) async {
    final lignes = {
      for (final e in _qty.entries)
        if (e.value > 0) e.key: e.value
    };
    if (lignes.isEmpty) return;
    if (!await GuestGate.ensureSession(context, ref)) return;
    if (!mounted) return;
    setState(() => _submitting = true);
    try {
      final order = await ref
          .read(ticketsRepositoryProvider)
          .createOrder(eventId: event.id, lignes: lignes);
      if (!mounted) return;
      if (order.montantTotal == 0) {
        // free tickets — order is already paid, tickets issued
        showSnack(context, 'Billets réservés. Retrouvez-les dans « Mes billets ».');
        context.go('/billets');
      } else {
        context.push('/paiement/TICKET_ORDER/${order.id}');
      }
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Acheter des billets')),
      body: FutureView<(EventDetail, List<EventTicketType>)>(
        future: _future,
        onRetry: () => setState(() => _future = _load()),
        builder: (data) {
          final (event, tickets) = data;
          if (tickets.isEmpty) {
            return const EmptyState(
              icon: Icons.confirmation_number_outlined,
              title: 'Aucun billet en vente',
              subtitle: 'La billetterie de cet événement n\'est pas ouverte.',
            );
          }
          return Column(
            children: [
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Text(event.nom,
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 12),
                    ...tickets.map((t) => _TicketRow(
                          ticket: t,
                          quantity: _qty[t.id] ?? 0,
                          onChanged: (v) => setState(() => _qty[t.id] = v),
                        )),
                  ],
                ),
              ),
              SafeArea(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('$_count billet(s)'),
                          Text(Fmt.money(_total(tickets)),
                              style: Theme.of(context).textTheme.titleLarge),
                        ],
                      ),
                      const SizedBox(height: 8),
                      FilledButton(
                        onPressed:
                            _count == 0 || _submitting ? null : () => _submit(event),
                        child: Text(_submitting
                            ? 'Création de la commande…'
                            : 'Commander'),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _TicketRow extends StatelessWidget {
  final EventTicketType ticket;
  final int quantity;
  final ValueChanged<int> onChanged;
  const _TicketRow({
    required this.ticket,
    required this.quantity,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final max = ticket.limiteParUtilisateur > 0
        ? ticket.limiteParUtilisateur
        : ticket.quantiteRestante;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(ticket.nom,
                style: const TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            Text(ticket.gratuit ? 'Gratuit' : ticket.prixFormatte,
                style: Theme.of(context).textTheme.bodyMedium),
            if (ticket.description != null) ...[
              const SizedBox(height: 4),
              Text(ticket.description!,
                  style: Theme.of(context).textTheme.bodySmall),
            ],
            if (ticket.portee == 'ACTIVITE' && ticket.activites.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text('Activités : ${ticket.activites.join(', ')}',
                  style: Theme.of(context).textTheme.bodySmall),
            ],
            const SizedBox(height: 8),
            Row(
              children: [
                Text('${ticket.quantiteRestante} restant(s)',
                    style: Theme.of(context).textTheme.bodySmall),
                const Spacer(),
                IconButton.outlined(
                  onPressed:
                      quantity > 0 ? () => onChanged(quantity - 1) : null,
                  icon: const Icon(Icons.remove),
                ),
                SizedBox(
                  width: 36,
                  child: Text('$quantity', textAlign: TextAlign.center),
                ),
                IconButton.outlined(
                  onPressed:
                      quantity < max ? () => onChanged(quantity + 1) : null,
                  icon: const Icon(Icons.add),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
