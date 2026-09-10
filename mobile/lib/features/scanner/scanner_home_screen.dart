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
  late Future<List<EventSummary>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(checkinRepositoryProvider).myControllableEvents();
  }

  void _refresh() => setState(() =>
      _future = ref.read(checkinRepositoryProvider).myControllableEvents());

  Future<void> _openEvent(EventSummary e) async {
    List<Activity> activities = const [];
    try {
      activities = await ref.read(checkinRepositoryProvider).eventActivities(e.id);
    } catch (_) {
      // fall back to a plain event-wide scan
    }
    if (!mounted) return;

    final nom = Uri.encodeComponent(e.nom);
    final cs = e.controleSortie ? '&controleSortie=1' : '';
    if (activities.isEmpty) {
      context.push('/scanner/${e.id}?nom=$nom$cs');
      return;
    }

    const generalEntry = '__general__';
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: Text('Que contrôlez-vous ?',
                  style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            ListTile(
              leading: const Icon(Icons.door_front_door_outlined),
              title: const Text('Entrée générale'),
              subtitle: const Text('Tout billet valable pour l\'événement'),
              onTap: () => Navigator.pop(ctx, generalEntry),
            ),
            const Divider(height: 1),
            ...activities.map((a) => ListTile(
                  leading: const Icon(Icons.event_note_outlined),
                  title: Text(a.titre),
                  subtitle: Text(a.gratuit
                      ? 'Accès gratuit'
                      : a.payant
                          ? 'Accès payant'
                          : 'Sur billet de l\'événement'),
                  onTap: () => Navigator.pop(ctx, a.id),
                )),
          ],
        ),
      ),
    );
    if (!mounted || choice == null) return; // sheet dismissed

    var url = '/scanner/${e.id}?nom=$nom$cs';
    if (choice != generalEntry) {
      final a = activities.firstWhere((x) => x.id == choice);
      url += '&activityId=${a.id}&activiteNom=${Uri.encodeComponent(a.titre)}';
    }
    context.push(url);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Contrôle à l\'entrée')),
      body: FutureView<List<EventSummary>>(
        future: _future,
        onRetry: _refresh,
        builder: (events) {
          if (events.isEmpty) {
            return const EmptyState(
              icon: Icons.qr_code_scanner,
              title: 'Aucun événement à contrôler',
              subtitle:
                  'Vous devez être organisateur de l\'événement, y être ajouté comme personnel de contrôle, ou être administrateur ; l\'événement doit être publié.',
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
                      onTap: () => _openEvent(e),
                    ),
                  )),
            ],
          );
        },
      ),
    );
  }
}
