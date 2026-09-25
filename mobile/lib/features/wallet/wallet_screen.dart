import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class WalletScreen extends ConsumerStatefulWidget {
  const WalletScreen({super.key});

  @override
  ConsumerState<WalletScreen> createState() => _WalletScreenState();
}

class _WalletScreenState extends ConsumerState<WalletScreen> {
  late Future<List<Ticket>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(ticketsRepositoryProvider).myTickets();
  }

  void _refresh() => setState(
      () => _future = ref.read(ticketsRepositoryProvider).myTickets());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mes billets'),
        actions: [
          IconButton(
            icon: const Icon(Icons.receipt_long_outlined),
            tooltip: 'Factures & reçus',
            onPressed: () => context.push('/factures'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _refresh(),
        child: FutureView<List<Ticket>>(
          future: _future,
          onRetry: _refresh,
          builder: (tickets) {
            if (tickets.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 120),
                EmptyState(
                  icon: Icons.confirmation_number_outlined,
                  title: 'Aucun billet',
                  subtitle:
                      'Vos billets électroniques apparaîtront ici après un achat confirmé.',
                  action: DiscoverEventsButton(),
                ),
              ]);
            }
            return MaxWidth(
              child: ListView.builder(
                padding: const EdgeInsets.all(12),
                itemCount: tickets.length,
                itemBuilder: (_, i) {
                  final t = tickets[i];
                  return Card(
                    clipBehavior: Clip.antiAlias,
                    margin: const EdgeInsets.only(bottom: 10),
                    child: InkWell(
                      onTap: () => context.push('/billets/${t.id}'),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              ClipRRect(
                                borderRadius: BorderRadius.circular(12),
                                child: EventCover(
                                  nom: t.eventNom,
                                  url: t.eventCoverUrl,
                                  width: 72,
                                  height: 72,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      StatusChip(t.statut),
                                      const SizedBox(height: 6),
                                      Text(t.eventNom,
                                          maxLines: 2,
                                          overflow: TextOverflow.ellipsis,
                                          style: Theme.of(context)
                                              .textTheme
                                              .titleSmall),
                                      const SizedBox(height: 2),
                                      Text(
                                          [
                                            if (t.categorieNom != null &&
                                                t.categorieNom!.isNotEmpty)
                                              t.categorieNom!,
                                            'N° ${t.numero}',
                                          ].join(' · '),
                                          style: Theme.of(context)
                                              .textTheme
                                              .bodySmall),
                                      Text(Fmt.dateTime(t.eventDateDebut),
                                          style: Theme.of(context)
                                              .textTheme
                                              .bodySmall),
                                    ]),
                              ),
                              const Icon(Icons.qr_code_2, color: Brand.s400),
                            ]),
                      ),
                    ),
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }
}
