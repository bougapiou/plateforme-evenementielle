import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class MyActivityScreen extends ConsumerWidget {
  const MyActivityScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 4,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Mon activité'),
          bottom: const TabBar(
            isScrollable: true,
            tabs: [
              Tab(text: 'Commandes'),
              Tab(text: 'Inscriptions'),
              Tab(text: 'Stands'),
              Tab(text: 'Paiements'),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _OrdersTab(),
            _RegistrationsTab(),
            _StandsTab(),
            _PaymentsTab(),
          ],
        ),
      ),
    );
  }
}

class _OrdersTab extends ConsumerStatefulWidget {
  const _OrdersTab();
  @override
  ConsumerState<_OrdersTab> createState() => _OrdersTabState();
}

class _OrdersTabState extends ConsumerState<_OrdersTab> {
  late Future<Paged<TicketOrder>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(ticketsRepositoryProvider).myOrders();
  }

  void _refresh() =>
      setState(() => _future = ref.read(ticketsRepositoryProvider).myOrders());

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async => _refresh(),
      child: FutureView<Paged<TicketOrder>>(
        future: _future,
        onRetry: _refresh,
        builder: (paged) {
          if (paged.content.isEmpty) {
            return _empty('Aucune commande de billets.');
          }
          return ListView(
            padding: const EdgeInsets.all(12),
            children: paged.content.map((o) {
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () async {
                    await context.push('/activite/commandes/${o.id}');
                    _refresh();
                  },
                  child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Expanded(
                          child: Text(o.eventNom,
                              style: Theme.of(context).textTheme.titleSmall),
                        ),
                        StatusChip(o.statut),
                      ]),
                      Text('Réf. ${o.reference}',
                          style: Theme.of(context).textTheme.bodySmall),
                      const SizedBox(height: 6),
                      ...o.lignes.map((l) => Text(
                          '${l.quantite} × ${l.ticketNom}',
                          style: Theme.of(context).textTheme.bodySmall)),
                      const SizedBox(height: 6),
                      Text(Fmt.price(o.montantTotal, o.devise),
                          style: const TextStyle(fontWeight: FontWeight.w600)),
                      if (o.enAttente) ...[
                        const SizedBox(height: 8),
                        Row(children: [
                          FilledButton(
                            onPressed: () async {
                              await context
                                  .push('/paiement/TICKET_ORDER/${o.id}');
                              _refresh();
                            },
                            child: const Text('Payer'),
                          ),
                          const SizedBox(width: 8),
                          TextButton(
                            onPressed: () async {
                              await ref
                                  .read(ticketsRepositoryProvider)
                                  .cancelOrder(o.id);
                              _refresh();
                            },
                            child: const Text('Annuler'),
                          ),
                        ]),
                      ],
                    ],
                  ),
                  ),
                ),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

class _RegistrationsTab extends ConsumerStatefulWidget {
  const _RegistrationsTab();
  @override
  ConsumerState<_RegistrationsTab> createState() => _RegistrationsTabState();
}

class _RegistrationsTabState extends ConsumerState<_RegistrationsTab> {
  late Future<Paged<Registration>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(registrationsRepositoryProvider).mine();
  }

  void _refresh() => setState(
      () => _future = ref.read(registrationsRepositoryProvider).mine());

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async => _refresh(),
      child: FutureView<Paged<Registration>>(
        future: _future,
        onRetry: _refresh,
        builder: (paged) {
          if (paged.content.isEmpty) return _empty('Aucune inscription.');
          return ListView(
            padding: const EdgeInsets.all(12),
            children: paged.content.map((r) {
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () async {
                    await context.push('/activite/inscriptions/${r.id}');
                    _refresh();
                  },
                  child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Expanded(
                          child: Text(r.eventNom,
                              style: Theme.of(context).textTheme.titleSmall),
                        ),
                        StatusChip(r.statut),
                      ]),
                      Text('Réf. ${r.reference} · ${r.type}',
                          style: Theme.of(context).textTheme.bodySmall),
                      Text('${r.nombreParticipants} participant(s)',
                          style: Theme.of(context).textTheme.bodySmall),
                      if (r.motifRefus != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text('Motif : ${r.motifRefus}',
                              style: const TextStyle(color: Color(0xFF991B1B))),
                        ),
                      if (r.ticketOrderId != null &&
                          r.ticketOrderStatut == 'EN_ATTENTE') ...[
                        const SizedBox(height: 8),
                        FilledButton(
                          onPressed: () async {
                            await context.push(
                                '/paiement/TICKET_ORDER/${r.ticketOrderId}');
                            _refresh();
                          },
                          child: const Text('Payer les billets'),
                        ),
                      ],
                    ],
                  ),
                  ),
                ),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

class _StandsTab extends ConsumerStatefulWidget {
  const _StandsTab();
  @override
  ConsumerState<_StandsTab> createState() => _StandsTabState();
}

class _StandsTabState extends ConsumerState<_StandsTab> {
  late Future<Paged<StandReservation>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(standsRepositoryProvider).mine();
  }

  void _refresh() =>
      setState(() => _future = ref.read(standsRepositoryProvider).mine());

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async => _refresh(),
      child: FutureView<Paged<StandReservation>>(
        future: _future,
        onRetry: _refresh,
        builder: (paged) {
          if (paged.content.isEmpty) {
            return _empty('Aucune réservation de stand.');
          }
          return ListView(
            padding: const EdgeInsets.all(12),
            children: paged.content.map((r) {
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () async {
                    await context.push('/activite/stands/${r.id}');
                    _refresh();
                  },
                  child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Expanded(
                          child: Text(r.eventNom,
                              style: Theme.of(context).textTheme.titleSmall),
                        ),
                        StatusChip(r.statut),
                      ]),
                      Text(
                          'Stand ${r.standNumero} — ${r.standTypeNom} · ${r.montantFormatte}',
                          style: Theme.of(context).textTheme.bodySmall),
                      Text('Réf. ${r.reference}',
                          style: Theme.of(context).textTheme.bodySmall),
                      if (r.aPayer) ...[
                        const SizedBox(height: 8),
                        Row(children: [
                          FilledButton(
                            onPressed: () async {
                              await context.push(
                                  '/paiement/STAND_RESERVATION/${r.id}');
                              _refresh();
                            },
                            child: const Text('Payer'),
                          ),
                          const SizedBox(width: 8),
                          TextButton(
                            onPressed: () async {
                              await ref
                                  .read(standsRepositoryProvider)
                                  .cancel(r.id);
                              _refresh();
                            },
                            child: const Text('Annuler'),
                          ),
                        ]),
                      ],
                    ],
                  ),
                  ),
                ),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

class _PaymentsTab extends ConsumerStatefulWidget {
  const _PaymentsTab();
  @override
  ConsumerState<_PaymentsTab> createState() => _PaymentsTabState();
}

class _PaymentsTabState extends ConsumerState<_PaymentsTab> {
  late Future<Paged<Payment>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(paymentsRepositoryProvider).mine();
  }

  void _refresh() =>
      setState(() => _future = ref.read(paymentsRepositoryProvider).mine());

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async => _refresh(),
      child: FutureView<Paged<Payment>>(
        future: _future,
        onRetry: _refresh,
        builder: (paged) {
          if (paged.content.isEmpty) return _empty('Aucun paiement.');
          return ListView(
            padding: const EdgeInsets.all(12),
            children: paged.content.map((p) {
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Text(p.montantFormatte),
                  subtitle: Text([
                    p.reference,
                    if (p.moyen != null) p.moyen!,
                    Fmt.dateTime(p.createdAt),
                  ].join('\n')),
                  isThreeLine: true,
                  trailing: StatusChip(p.statut),
                ),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

Widget _empty(String msg) => ListView(children: [
      const SizedBox(height: 100),
      EmptyState(icon: Icons.inbox_outlined, title: msg),
    ]);
