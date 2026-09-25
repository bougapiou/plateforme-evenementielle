import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
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
          if (!signedIn)
            TextButton(
              onPressed: () => context.push('/connexion'),
              child: const Text('Connexion'),
            ),
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
                const SizedBox(height: 10),
                // accès rapides : libellés clairs plutôt que des icônes seules
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(children: [
                    _QuickAction(
                      icon: Icons.sensors,
                      label: 'Présence en direct',
                      onTap: () => context.push('/presence-en-direct'),
                    ),
                    const SizedBox(width: 8),
                    _QuickAction(
                      icon: Icons.qr_code_scanner,
                      label: 'Scanner un QR',
                      onTap: () => context.push('/scanner-qr'),
                    ),
                    if (!signedIn) ...[
                      const SizedBox(width: 8),
                      _QuickAction(
                        icon: Icons.confirmation_number_outlined,
                        label: 'Retrouver mon billet',
                        onTap: () => context.push('/retrouver-billet'),
                      ),
                    ],
                  ]),
                ),
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

/// Translucent pill on the green header: a labelled shortcut.
class _QuickAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  const _QuickAction(
      {required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white.withValues(alpha: .14),
      shape: StadiumBorder(
          side: BorderSide(color: Colors.white.withValues(alpha: .28))),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 16, color: Colors.white),
            const SizedBox(width: 6),
            Text(label,
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 13,
                    fontWeight: FontWeight.w600)),
          ]),
        ),
      ),
    );
  }
}

class _EventCard extends StatelessWidget {
  final EventSummary event;
  const _EventCard({required this.event});

  static final _month = DateFormat('MMM', 'fr');

  void _openDetail(BuildContext context) =>
      context.push('/evenements/${event.slug}');

  /// Same day: opening hours; several days: the date range.
  String _when() {
    final d = event.dateDebut?.toLocal();
    final f = event.dateFin?.toLocal();
    if (d != null &&
        f != null &&
        d.year == f.year &&
        d.month == f.month &&
        d.day == f.day) {
      return '${Fmt.time(d)} – ${Fmt.time(f)}';
    }
    return Fmt.range(event.dateDebut, event.dateFin);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final d = event.dateDebut?.toLocal();
    final place = [event.ville, event.lieu]
        .where((e) => e != null && e.isNotEmpty)
        .join(' · ');

    return Card(
      clipBehavior: Clip.antiAlias,
      margin: const EdgeInsets.only(bottom: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Image : un appui ouvre le détail de l'événement.
          InkWell(
            onTap: () => _openDetail(context),
            child: Stack(
              children: [
                AspectRatio(
                  aspectRatio: 16 / 8,
                  child: EventCover(nom: event.nom, url: event.coverUrl),
                ),
                if (d != null)
                  Positioned(
                    top: 10,
                    left: 10,
                    child: Container(
                      padding:
                          const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: const [
                          BoxShadow(color: Colors.black26, blurRadius: 4)
                        ],
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(d.day.toString().padLeft(2, '0'),
                              style: const TextStyle(
                                  fontSize: 20,
                                  fontWeight: FontWeight.w800,
                                  height: 1.1)),
                          Text(_month.format(d).replaceAll('.', '').toUpperCase(),
                              style: const TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: Brand.b600)),
                          Text('${d.year}',
                              style: const TextStyle(
                                  fontSize: 10, color: Brand.s500)),
                        ],
                      ),
                    ),
                  ),
                if (event.categoryNom != null)
                  Positioned(
                    bottom: 10,
                    left: 10,
                    child: Container(
                      padding:
                          const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: Brand.b600,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(event.categoryNom!,
                          style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                              fontWeight: FontWeight.w600)),
                    ),
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                InkWell(
                  onTap: () => _openDetail(context),
                  child: Text(event.nom,
                      style: theme.textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.w700),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis),
                ),
                const SizedBox(height: 8),
                if (place.isNotEmpty) _line(context, Icons.place_outlined, place),
                _line(context, Icons.schedule, _when()),
                // Description : un appui ouvre le détail de l'événement.
                if (event.descriptionCourte != null) ...[
                  const SizedBox(height: 8),
                  InkWell(
                    onTap: () => _openDetail(context),
                    child: Text(event.descriptionCourte!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodyMedium),
                  ),
                ],
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: () => context
                        .push('/evenements/${event.slug}?participer=1'),
                    icon: const Icon(Icons.confirmation_number_outlined),
                    label: const Text("S'inscrire et prendre un billet"),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _line(BuildContext context, IconData icon, String text) => Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Row(
          children: [
            Icon(icon, size: 16, color: Theme.of(context).colorScheme.outline),
            const SizedBox(width: 8),
            Expanded(
                child: Text(text,
                    style: Theme.of(context).textTheme.bodySmall)),
          ],
        ),
      );
}
