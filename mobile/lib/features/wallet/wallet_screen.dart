import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
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
                ),
              ]);
            }
            return ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: tickets.length,
              itemBuilder: (_, i) {
                final t = tickets[i];
                return Card(
                  clipBehavior: Clip.antiAlias,
                  margin: const EdgeInsets.only(bottom: 10),
                  child: ListTile(
                    onTap: () => context.push('/billets/${t.id}'),
                    leading: t.eventCoverUrl != null
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: RemoteImage(
                              url: t.eventCoverUrl,
                              width: 44,
                              height: 44,
                            ),
                          )
                        : const CircleAvatar(
                            child: Icon(Icons.qr_code_2),
                          ),
                    title: Text(t.eventNom,
                        maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text([
                      t.categorieNom ?? '',
                      Fmt.dateTime(t.eventDateDebut),
                      'N° ${t.numero}',
                    ].where((s) => s.isNotEmpty).join('\n')),
                    isThreeLine: true,
                    trailing: StatusChip(t.statut),
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }
}
