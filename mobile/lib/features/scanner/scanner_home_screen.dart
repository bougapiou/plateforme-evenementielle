import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

/// Lets entry-control staff pick the event they are checking people in for.
class ScannerHomeScreen extends ConsumerStatefulWidget {
  const ScannerHomeScreen({super.key});

  @override
  ConsumerState<ScannerHomeScreen> createState() => _ScannerHomeScreenState();
}

class _ScannerHomeScreenState extends ConsumerState<ScannerHomeScreen> {
  late Future<Paged<EventSummary>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(checkinRepositoryProvider).myControllableEvents();
  }

  void _refresh() => setState(() =>
      _future = ref.read(checkinRepositoryProvider).myControllableEvents());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Contrôle à l\'entrée')),
      body: FutureView<Paged<EventSummary>>(
        future: _future,
        onRetry: _refresh,
        builder: (paged) {
          final events = paged.content
              .where((e) =>
                  e.statut == 'EN_COURS' ||
                  e.statut == 'INSCRIPTIONS_FERMEES' ||
                  e.statut == 'INSCRIPTIONS_OUVERTES' ||
                  e.statut == 'PUBLIE')
              .toList();
          if (events.isEmpty) {
            return const EmptyState(
              icon: Icons.qr_code_scanner,
              title: 'Aucun événement à contrôler',
              subtitle:
                  'Sélectionnez un événement dont vous êtes organisateur ou personnel de contrôle.',
            );
          }
          return ListView(
            padding: const EdgeInsets.all(12),
            children: [
              Padding(
                padding: const EdgeInsets.all(8),
                child: Text(
                  'Choisissez l\'événement, puis scannez les QR codes des billets.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
              ...events.map((e) => Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      title: Text(e.nom),
                      subtitle: Text([
                        Fmt.range(e.dateDebut, e.dateFin),
                        if (e.ville != null) e.ville!,
                      ].join(' · ')),
                      trailing: const Icon(Icons.qr_code_scanner),
                      onTap: () => context.push(
                        '/scanner/${e.id}?nom=${Uri.encodeComponent(e.nom)}',
                      ),
                    ),
                  )),
            ],
          );
        },
      ),
    );
  }
}
