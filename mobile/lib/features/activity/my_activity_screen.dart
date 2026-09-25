import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

/// Colour of an order / registration / payment by where it stands.
KitTone _statusTone(String statut) {
  switch (statut) {
    case 'PAYEE':
    case 'PAYE':
    case 'CONFIRMEE':
    case 'CONFIRME':
    case 'REUSSI':
      return KitTone.green;
    case 'EN_ATTENTE':
    case 'ATTENTE_PAIEMENT':
    case 'RESERVE_TEMP':
      return KitTone.amber;
    case 'ANNULEE':
    case 'ANNULE':
    case 'REFUSEE':
    case 'ECHOUE':
    case 'EXPIREE':
    case 'EXPIRE':
      return KitTone.red;
    default:
      return KitTone.slate;
  }
}

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
            tabAlignment: TabAlignment.start,
            tabs: [
              Tab(icon: Icon(Icons.confirmation_number_outlined), text: 'Commandes'),
              Tab(icon: Icon(Icons.how_to_reg_outlined), text: 'Inscriptions'),
              Tab(icon: Icon(Icons.storefront_outlined), text: 'Stands'),
              Tab(icon: Icon(Icons.payments_outlined), text: 'Paiements'),
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

/// One row of "Mon activité": what it is, where it stands, how much, and what can be done.
class _ActivityItem extends StatelessWidget {
  final IconData icon;
  final String title;
  final String reference;
  final List<String> lines;
  final String statut;
  final String? amount;
  final String? warning;
  final List<Widget> actions;
  final VoidCallback? onTap;

  const _ActivityItem({
    required this.icon,
    required this.title,
    required this.reference,
    required this.statut,
    this.lines = const [],
    this.amount,
    this.warning,
    this.actions = const [],
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final tone = _statusTone(statut);
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                IconBubble(icon, tone: tone, size: 42),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 2),
                    Text(reference, style: Theme.of(context).textTheme.bodySmall),
                    for (final l in lines)
                      Text(l, style: Theme.of(context).textTheme.bodySmall),
                  ]),
                ),
                const SizedBox(width: 8),
                Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
                  StatusChip(statut),
                  if (amount != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(amount!,
                          style: const TextStyle(
                              fontWeight: FontWeight.w800, fontSize: 15, color: Brand.s800)),
                    ),
                ]),
              ]),
              if (warning != null) ...[
                const SizedBox(height: 10),
                InfoBanner(warning!, tone: KitTone.red),
              ],
              if (actions.isNotEmpty) ...[
                const SizedBox(height: 12),
                Row(children: [
                  for (int i = 0; i < actions.length; i++) ...[
                    if (i > 0) const SizedBox(width: 8),
                    Expanded(child: actions[i]),
                  ],
                ]),
              ],
            ],
          ),
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

  Future<void> _cancel(TicketOrder o) async {
    final ok = await confirmAction(
      context,
      title: 'Annuler cette commande ?',
      message: '${o.eventNom} — réf. ${o.reference}. Les billets seront libérés.',
      confirmLabel: 'Annuler la commande',
      destructive: true,
    );
    if (!ok) return;
    await ref.read(ticketsRepositoryProvider).cancelOrder(o.id);
    _refresh();
  }

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
          return MaxWidth(
            child: ListView(
              padding: const EdgeInsets.all(12),
              children: paged.content.map((o) {
                return _ActivityItem(
                  icon: Icons.confirmation_number_outlined,
                  title: o.eventNom,
                  reference: 'Réf. ${o.reference}',
                  lines: [for (final l in o.lignes) '${l.quantite} × ${l.ticketNom}'],
                  statut: o.statut,
                  amount: Fmt.price(o.montantTotal, o.devise),
                  onTap: () async {
                    await context.push('/activite/commandes/${o.id}');
                    _refresh();
                  },
                  actions: o.enAttente
                      ? [
                          FilledButton(
                            onPressed: () async {
                              await context.push('/paiement/TICKET_ORDER/${o.id}');
                              _refresh();
                            },
                            child: const Text('Payer'),
                          ),
                          OutlinedButton(
                            onPressed: () => _cancel(o),
                            child: const Text('Annuler'),
                          ),
                        ]
                      : const [],
                );
              }).toList(),
            ),
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
          return MaxWidth(
            child: ListView(
              padding: const EdgeInsets.all(12),
              children: paged.content.map((r) {
                return _ActivityItem(
                  icon: Icons.how_to_reg_outlined,
                  title: r.eventNom,
                  reference: 'Réf. ${r.reference} · ${r.type}',
                  lines: ['${r.nombreParticipants} participant(s)'],
                  statut: r.statut,
                  warning: r.motifRefus != null ? 'Motif : ${r.motifRefus}' : null,
                  onTap: () async {
                    await context.push('/activite/inscriptions/${r.id}');
                    _refresh();
                  },
                  actions: r.ticketOrderId != null && r.ticketOrderStatut == 'EN_ATTENTE'
                      ? [
                          FilledButton(
                            onPressed: () async {
                              await context
                                  .push('/paiement/TICKET_ORDER/${r.ticketOrderId}');
                              _refresh();
                            },
                            child: const Text('Payer les billets'),
                          ),
                        ]
                      : const [],
                );
              }).toList(),
            ),
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

  Future<void> _cancel(StandReservation r) async {
    final ok = await confirmAction(
      context,
      title: 'Annuler cette réservation ?',
      message: '${r.eventNom} — stand ${r.standNumero}. Il sera remis à la disposition des autres exposants.',
      confirmLabel: 'Annuler la réservation',
      destructive: true,
    );
    if (!ok) return;
    await ref.read(standsRepositoryProvider).cancel(r.id);
    _refresh();
  }

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
          return MaxWidth(
            child: ListView(
              padding: const EdgeInsets.all(12),
              children: paged.content.map((r) {
                return _ActivityItem(
                  icon: Icons.storefront_outlined,
                  title: r.eventNom,
                  reference: 'Réf. ${r.reference}',
                  lines: ['Stand ${r.standNumero} — ${r.standTypeNom}'],
                  statut: r.statut,
                  amount: r.montantFormatte,
                  onTap: () async {
                    await context.push('/activite/stands/${r.id}');
                    _refresh();
                  },
                  actions: r.aPayer
                      ? [
                          FilledButton(
                            onPressed: () async {
                              await context.push('/paiement/STAND_RESERVATION/${r.id}');
                              _refresh();
                            },
                            child: const Text('Payer'),
                          ),
                          OutlinedButton(
                            onPressed: () => _cancel(r),
                            child: const Text('Annuler'),
                          ),
                        ]
                      : const [],
                );
              }).toList(),
            ),
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
          return MaxWidth(
            child: ListView(
              padding: const EdgeInsets.all(12),
              children: paged.content.map((p) {
                return _ActivityItem(
                  icon: Icons.payments_outlined,
                  title: p.montantFormatte,
                  reference: p.reference,
                  lines: [
                    if (p.moyen != null) p.moyen!,
                    Fmt.dateTime(p.createdAt),
                  ],
                  statut: p.statut,
                );
              }).toList(),
            ),
          );
        },
      ),
    );
  }
}

Widget _empty(String msg) => ListView(children: [
      const SizedBox(height: 100),
      EmptyState(
        icon: Icons.inbox_outlined,
        title: msg,
        action: const DiscoverEventsButton(),
      ),
    ]);
