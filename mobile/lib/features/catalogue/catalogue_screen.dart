import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/faso.dart' show FasoMark;
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class CatalogueScreen extends ConsumerStatefulWidget {
  const CatalogueScreen({super.key});

  @override
  ConsumerState<CatalogueScreen> createState() => _CatalogueScreenState();
}

class _CatalogueScreenState extends ConsumerState<CatalogueScreen> {
  final _searchCtrl = TextEditingController();
  String _search = '';
  String? _categorie;
  List<EventCategory> _categories = [];
  late Future<Paged<EventSummary>> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
    _loadCategories();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<Paged<EventSummary>> _load() =>
      ref.read(eventsRepositoryProvider).search(q: _search, categorie: _categorie);

  Future<void> _loadCategories() async {
    try {
      final cats = await ref.read(eventsRepositoryProvider).categories();
      if (mounted) setState(() => _categories = cats);
    } catch (_) {/* filters simply stay hidden */}
  }

  void _refresh() => setState(() => _future = _load());

  @override
  Widget build(BuildContext context) {
    final signedIn = ref.watch(authControllerProvider).valueOrNull != null;

    return Scaffold(
      appBar: AppBar(
        title: const FasoMark(height: 26),
        actions: [
          IconButton(
            icon: const Icon(Icons.qr_code_scanner),
            tooltip: 'Scanner un QR code',
            onPressed: () => context.push('/scanner-qr'),
          ),
          if (!signedIn) ...[
            IconButton(
              icon: const Icon(Icons.confirmation_number_outlined),
              tooltip: 'Retrouver mon billet',
              onPressed: () => context.push('/retrouver-billet'),
            ),
            TextButton(
              onPressed: () => context.push('/connexion'),
              child: const Text('Connexion'),
            ),
          ],
        ],
      ),
      body: Column(
        children: [
          Container(
            width: double.infinity,
            color: Brand.b700,
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Grands événements du Burkina Faso',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: Colors.white, fontWeight: FontWeight.w700)),
                const SizedBox(height: 2),
                Text('SIAO · FESPACO · Semaine du Numérique · salons & foires',
                    style: Theme.of(context)
                        .textTheme
                        .bodySmall
                        ?.copyWith(color: Colors.white70)),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
            child: TextField(
              controller: _searchCtrl,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: 'Rechercher un événement, une ville…',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _search.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () {
                          _searchCtrl.clear();
                          setState(() => _search = '');
                          _refresh();
                        },
                      ),
              ),
              onSubmitted: (v) {
                setState(() => _search = v.trim());
                _refresh();
              },
            ),
          ),
          if (_categories.isNotEmpty)
            SizedBox(
              height: 44,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                children: [
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: FilterChip(
                      label: const Text('Toutes'),
                      selected: _categorie == null,
                      onSelected: (_) {
                        setState(() => _categorie = null);
                        _refresh();
                      },
                    ),
                  ),
                  ..._categories.map((c) => Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: FilterChip(
                          label: Text(c.nom),
                          selected: _categorie == c.slug,
                          onSelected: (_) {
                            setState(() =>
                                _categorie = _categorie == c.slug ? null : c.slug);
                            _refresh();
                          },
                        ),
                      )),
                ],
              ),
            ),
          const SizedBox(height: 4),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () async => _refresh(),
              child: FutureView<Paged<EventSummary>>(
                future: _future,
                onRetry: _refresh,
                builder: (paged) {
                  if (paged.content.isEmpty) {
                    return ListView(children: const [
                      SizedBox(height: 120),
                      EmptyState(
                        icon: Icons.event_busy,
                        title: 'Aucun événement',
                        subtitle: 'Aucun événement ne correspond à votre recherche.',
                      ),
                    ]);
                  }
                  return ListView.builder(
                    padding: const EdgeInsets.fromLTRB(12, 4, 12, 20),
                    itemCount: paged.content.length,
                    itemBuilder: (_, i) => _EventCard(event: paged.content[i]),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _EventCard extends StatelessWidget {
  final EventSummary event;
  const _EventCard({required this.event});

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () => context.push('/evenements/${event.slug}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                AspectRatio(
                  aspectRatio: 16 / 7,
                  child: RemoteImage(url: event.coverUrl, fallbackIcon: Icons.event),
                ),
                Positioned(
                  top: 8,
                  right: 8,
                  child: StatusChip(event.statut),
                ),
              ],
            ),
            Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (event.categoryNom != null)
                Text(
                  event.categoryNom!.toUpperCase(),
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: Theme.of(context).colorScheme.primary,
                        letterSpacing: .5,
                      ),
                ),
              const SizedBox(height: 4),
              Text(event.nom,
                  style: Theme.of(context).textTheme.titleMedium,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis),
              if (event.descriptionCourte != null) ...[
                const SizedBox(height: 4),
                Text(event.descriptionCourte!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall),
              ],
              const SizedBox(height: 10),
              _line(context, Icons.calendar_today,
                  Fmt.range(event.dateDebut, event.dateFin)),
              if ((event.ville ?? event.lieu) != null)
                _line(context, Icons.place_outlined,
                    [event.lieu, event.ville].where((e) => e != null).join(', ')),
              if (event.standsActifs)
                _line(context, Icons.storefront_outlined,
                    'Réservation de stands ouverte'),
            ],
          ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _line(BuildContext context, IconData icon, String text) => Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Row(
          children: [
            Icon(icon, size: 15, color: Theme.of(context).colorScheme.outline),
            const SizedBox(width: 8),
            Expanded(
                child: Text(text,
                    style: Theme.of(context).textTheme.bodySmall)),
          ],
        ),
      );
}
