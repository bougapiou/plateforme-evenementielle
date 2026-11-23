import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/faso.dart';
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../data/domain.dart';

/// Accueil : bandeau de recherche, à la une, catégories, catalogue (à venir /
/// passés), fonctionnement, organisateurs. Charte graphique BF v2.0.
class CatalogueScreen extends ConsumerStatefulWidget {
  const CatalogueScreen({super.key});

  @override
  ConsumerState<CatalogueScreen> createState() => _CatalogueScreenState();
}

class _CatalogueScreenState extends ConsumerState<CatalogueScreen> {
  final _searchCtrl = TextEditingController();
  final _catalogueKey = GlobalKey();
  String _search = '';
  String? _categorie;
  bool _passes = false;

  List<EventCategory> _categories = [];
  List<EventSummary> _tous = [];
  List<EventSummary> _resultats = [];
  bool _loading = true;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _loadAll();
    _loadCategories();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  bool get _filtres => _search.isNotEmpty || _categorie != null;

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final p = await ref.read(eventsRepositoryProvider).search(size: 100);
      if (!mounted) return;
      setState(() {
        _tous = p.content;
        if (!_filtres) _resultats = p.content;
        _loading = false;
      });
      if (_filtres) await _rechercher();
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _rechercher({bool scroll = false}) async {
    setState(() => _loading = true);
    try {
      final p = await ref
          .read(eventsRepositoryProvider)
          .search(q: _search, categorie: _categorie, size: 100);
      if (!mounted) return;
      setState(() {
        _resultats = p.content;
        _loading = false;
        if (_filtres) _passes = _aVenir.isEmpty && _passesList.isNotEmpty;
      });
      if (scroll && _catalogueKey.currentContext != null) {
        Scrollable.ensureVisible(_catalogueKey.currentContext!,
            duration: const Duration(milliseconds: 360), curve: Curves.easeOutCubic);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _loadCategories() async {
    try {
      final cats = await ref.read(eventsRepositoryProvider).categories();
      if (mounted) setState(() => _categories = cats);
    } catch (_) {/* les catégories restent masquées */}
  }

  // ---------- données dérivées ----------
  bool _estPasse(EventSummary e) =>
      (e.dateFin ?? e.dateDebut ?? DateTime.now()).isBefore(DateTime.now());
  int _asc(EventSummary a, EventSummary b) =>
      (a.dateDebut ?? DateTime(0)).compareTo(b.dateDebut ?? DateTime(0));

  List<EventSummary> get _aVenirTous => _tous.where((e) => !_estPasse(e)).toList()..sort(_asc);
  List<EventSummary> get _aVenir => _resultats.where((e) => !_estPasse(e)).toList()..sort(_asc);
  List<EventSummary> get _passesList =>
      _resultats.where(_estPasse).toList()..sort((a, b) => _asc(b, a));

  EventSummary? get _aLaUne {
    final a = _aVenirTous;
    if (a.isEmpty) return null;
    return a.firstWhere((e) => e.statut == 'INSCRIPTIONS_OUVERTES' || e.statut == 'EN_COURS',
        orElse: () => a.first);
  }

  int get _nbVilles => _tous.map((e) => e.ville).whereType<String>().where((v) => v.isNotEmpty).toSet().length;

  void _effacer() {
    _searchCtrl.clear();
    setState(() {
      _search = '';
      _categorie = null;
    });
    _rechercher();
  }

  @override
  Widget build(BuildContext context) {
    final signedIn = ref.watch(authControllerProvider).valueOrNull != null;
    final liste = _passes ? _passesList : _aVenir;
    final une = _aLaUne;

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: _loadAll,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              pinned: true,
              toolbarHeight: 64,
              titleSpacing: 16,
              title: Semantics(
                label: 'PNE — Plateforme Nationale des Événements',
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Image.asset('assets/logo_pne_mark.png', height: 24, fit: BoxFit.contain, excludeFromSemantics: true),
                  Container(width: 1, height: 30, margin: const EdgeInsets.symmetric(horizontal: 8), color: Brand.s300),
                  const Flexible(
                    child: ExcludeSemantics(
                      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('Plateforme Nationale',
                            maxLines: 2,
                            softWrap: true,
                            style: TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w800, fontSize: 12.5, height: 1.15, color: Brand.s900)),
                        Text('des Événements',
                            maxLines: 1,
                            style: TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w500, fontSize: 11.5, height: 1.15, color: Brand.s700)),
                      ]),
                    ),
                  ),
                ]),
              ),
              bottom: const PreferredSize(preferredSize: Size.fromHeight(8), child: FasoFilet(height: 8)),
            ),

            // ============ BANDEAU ============
            SliverToBoxAdapter(child: _hero(context, signedIn)),

            // ============ À LA UNE ============
            if (une != null && !_filtres)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 28, 16, 0),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Surtitre('À la une'),
                    const SizedBox(height: 10),
                    _FeaturedCard(event: une),
                  ]),
                ),
              ),

            // ============ CATALOGUE ============
            SliverToBoxAdapter(
              child: Padding(
                key: _catalogueKey,
                padding: const EdgeInsets.fromLTRB(16, 32, 16, 0),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Surtitre('Catalogue'),
                  const SizedBox(height: 6),
                  Row(children: [
                    Expanded(
                      child: Text(_passes ? 'Événements passés' : 'Événements à venir',
                          style: Theme.of(context).textTheme.headlineSmall),
                    ),
                  ]),
                  const SizedBox(height: 14),
                  _Onglets(
                    passes: _passes,
                    nAVenir: _aVenir.length,
                    nPasses: _passesList.length,
                    onChanged: (v) => setState(() => _passes = v),
                  ),
                  // Catégorie : liste déroulante (évite de faire défiler des tuiles avant les événements)
                  if (_categories.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    DropdownButtonFormField<String>(
                      // la clé recrée le champ quand la catégorie change ailleurs (« Tout effacer »)
                      key: ValueKey(_categorie ?? ''),
                      initialValue: _categorie ?? '',
                      isExpanded: true,
                      decoration: const InputDecoration(
                        labelText: 'Catégorie',
                        prefixIcon: Icon(Icons.category_outlined),
                      ),
                      items: [
                        const DropdownMenuItem(value: '', child: Text('Toutes les catégories')),
                        for (final c in _categories)
                          DropdownMenuItem(
                            value: c.slug,
                            child: Row(children: [
                              Icon(_iconeCategorie(c.slug), size: 18, color: Brand.b700),
                              const SizedBox(width: 10),
                              Expanded(child: Text(c.nom, overflow: TextOverflow.ellipsis)),
                              Text(_compteCategorie(c.nom),
                                  style: const TextStyle(fontSize: 12, color: Brand.s500)),
                            ]),
                          ),
                      ],
                      onChanged: (v) {
                        setState(() => _categorie = (v == null || v.isEmpty) ? null : v);
                        _rechercher();
                      },
                    ),
                  ],
                  if (_filtres) ...[
                    const SizedBox(height: 12),
                    Wrap(spacing: 8, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
                      if (_search.isNotEmpty) _FiltreChip('« $_search »'),
                      if (_categorie != null)
                        _FiltreChip(_categories.where((c) => c.slug == _categorie).firstOrNull?.nom ?? _categorie!),
                      TextButton(onPressed: _effacer, child: const Text('Tout effacer')),
                    ]),
                  ],
                  const SizedBox(height: 16),
                ]),
              ),
            ),
            if (_loading)
              SliverList.separated(
                itemCount: 2,
                separatorBuilder: (_, __) => const SizedBox(height: 16),
                itemBuilder: (_, __) => const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 16),
                  child: _SkeletonCard(),
                ),
              )
            else if (_error != null)
              SliverToBoxAdapter(
                child: _Vide(
                  icon: Icons.cloud_off_outlined,
                  titre: 'Impossible de charger les événements',
                  texte: 'Vérifiez votre connexion puis réessayez.',
                  action: OutlinedButton.icon(
                      onPressed: _loadAll, icon: const Icon(Icons.refresh), label: const Text('Réessayer')),
                ),
              )
            else if (liste.isEmpty)
              SliverToBoxAdapter(
                child: _Vide(
                  icon: Icons.event_busy_outlined,
                  titre: 'Aucun événement trouvé',
                  texte: _filtres
                      ? 'Essayez d’autres mots-clés ou effacez les filtres.'
                      : _passes
                          ? 'Aucun événement passé à afficher.'
                          : 'Aucun événement à venir pour le moment. Revenez bientôt.',
                  action: _filtres
                      ? OutlinedButton(onPressed: _effacer, child: const Text('Effacer les filtres'))
                      : null,
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                sliver: SliverList.separated(
                  itemCount: liste.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 16),
                  itemBuilder: (_, i) => _EventCard(event: liste[i], passe: _passes),
                ),
              ),

            // ============ COMMENT ÇA MARCHE ============
            SliverToBoxAdapter(child: _etapes(context)),

            // ============ ORGANISATEURS ============
            SliverToBoxAdapter(child: _organisateurs(context)),

            // ============ PIED ============
            SliverToBoxAdapter(
              child: Container(
                color: Brand.s900,
                padding: const EdgeInsets.fromLTRB(16, 24, 16, 28),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Semantics(
                    label: 'PNE — Plateforme Nationale des Événements',
                    child: Row(children: [
                      // Logo sur fond blanc : ses parties claires disparaîtraient sur le fond sombre.
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                        decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6)),
                        child: Image.asset('assets/logo_pne_mark.png', height: 28, excludeFromSemantics: true),
                      ),
                      const SizedBox(width: 12),
                      const ExcludeSemantics(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text('Plateforme Nationale',
                              style: TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w800, fontSize: 15, color: Colors.white)),
                          Text('des Événements',
                              style: TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w500, fontSize: 13, color: Brand.s300)),
                        ]),
                      ),
                    ]),
                  ),
                  const SizedBox(height: 14),
                  Text('© ${DateTime.now().year} PNE — Plateforme Nationale des Événements',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Brand.s400)),
                ]),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ---------- sections ----------
  Widget _hero(BuildContext context, bool signedIn) {
    final t = Theme.of(context).textTheme;
    return Container(
      color: Brand.s900,
      child: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 28, 16, 28),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text.rich(
              TextSpan(children: [
                const TextSpan(text: 'Les grands événements du Burkina Faso, '),
                TextSpan(text: 'au même endroit.', style: TextStyle(color: Brand.gold.withValues(alpha: 1))),
              ]),
              style: t.headlineLarge?.copyWith(color: Colors.white, fontSize: 31),
            ),
            const SizedBox(height: 12),
            Text(
              'Salons, foires, festivals, forums et conférences : inscrivez-vous, payez en ligne et recevez votre billet en quelques minutes.',
              style: t.bodyLarge?.copyWith(color: Brand.s300, height: 1.6),
            ),
            const SizedBox(height: 20),
            // Recherche
            Material(
              color: Colors.white,
              borderRadius: BorderRadius.circular(10),
              elevation: 6,
              shadowColor: Colors.black45,
              child: Padding(
                padding: const EdgeInsets.all(6),
                child: Row(children: [
                  const SizedBox(width: 8),
                  const Icon(Icons.search, color: Brand.s500),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: _searchCtrl,
                      textInputAction: TextInputAction.search,
                      decoration: const InputDecoration(
                        hintText: 'Événement, ville, mot-clé…',
                        filled: false,
                        border: InputBorder.none,
                        enabledBorder: InputBorder.none,
                        focusedBorder: InputBorder.none,
                        isDense: true,
                        contentPadding: EdgeInsets.symmetric(vertical: 12),
                      ),
                      onSubmitted: (v) {
                        setState(() => _search = v.trim());
                        _rechercher(scroll: true);
                      },
                    ),
                  ),
                  SizedBox(
                    height: 44,
                    child: FilledButton(
                      style: FilledButton.styleFrom(minimumSize: const Size(44, 44), padding: const EdgeInsets.symmetric(horizontal: 14)),
                      onPressed: () {
                        FocusScope.of(context).unfocus();
                        setState(() => _search = _searchCtrl.text.trim());
                        _rechercher(scroll: true);
                      },
                      child: const Icon(Icons.arrow_forward, size: 20, semanticLabel: 'Rechercher'),
                    ),
                  ),
                ]),
              ),
            ),
            const SizedBox(height: 14),
            Wrap(spacing: 8, runSpacing: 8, children: [
              _PilluleClaire(
                  icon: Icons.sensors, label: 'Présence en direct', onTap: () => context.push('/presence-en-direct')),
              _PilluleClaire(
                  icon: Icons.qr_code_scanner, label: 'Scanner un billet', onTap: () => context.push('/scanner-qr')),
              if (!signedIn)
                _PilluleClaire(
                    icon: Icons.confirmation_number_outlined,
                    label: 'Retrouver mon billet',
                    onTap: () => context.push('/retrouver-billet')),
            ]),
            const SizedBox(height: 22),
            Container(
              padding: const EdgeInsets.only(top: 16),
              decoration: BoxDecoration(border: Border(top: BorderSide(color: Colors.white.withValues(alpha: .12)))),
              child: Row(children: [
                _Chiffre(valeur: '${_aVenirTous.length}', label: 'À venir'),
                _Chiffre(valeur: '$_nbVilles', label: 'Villes'),
                _Chiffre(valeur: '${_categories.length}', label: 'Catégories'),
              ]),
            ),
          ]),
        ),
        const FasoTissage(),
      ]),
    );
  }

  Widget _etapes(BuildContext context) {
    const etapes = [
      (Icons.search, 'Trouvez votre événement', 'Parcourez le catalogue par catégorie, ville ou mot-clé.'),
      (Icons.confirmation_number_outlined, 'Inscrivez-vous en ligne', 'Un numéro de téléphone suffit. Payez en ligne si l’accès est payant.'),
      (Icons.qr_code_2, 'Présentez votre QR code', 'Votre billet s’affiche sur votre téléphone et se scanne à l’entrée.'),
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 44, 16, 36),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Surtitre('Simple et rapide'),
        const SizedBox(height: 6),
        Text('Votre billet en trois étapes', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 20),
        for (var i = 0; i < etapes.length; i++)
          IntrinsicHeight(
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Column(children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: const BoxDecoration(color: Brand.b600, shape: BoxShape.circle),
                  alignment: Alignment.center,
                  child: Icon(etapes[i].$1, color: Colors.white, size: 22),
                ),
                if (i < etapes.length - 1)
                  Expanded(child: Container(width: 2, margin: const EdgeInsets.symmetric(vertical: 4), color: Brand.s200)),
              ]),
              const SizedBox(width: 14),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(top: 2, bottom: 22),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${i + 1}. ${etapes[i].$2}', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 4),
                    Text(etapes[i].$3, style: Theme.of(context).textTheme.bodySmall),
                  ]),
                ),
              ),
            ]),
          ),
        const SizedBox(height: 4),
        Wrap(spacing: 16, runSpacing: 10, children: const [
          _Garantie(Icons.verified_user_outlined, 'Paiement sécurisé'),
          _Garantie(Icons.qr_code_2, 'Billet QR infalsifiable'),
          _Garantie(Icons.print_disabled_outlined, 'Sans impression'),
        ]),
      ]),
    );
  }

  Widget _organisateurs(BuildContext context) {
    const atouts = [
      (Icons.confirmation_number_outlined, 'Billetterie et inscriptions'),
      (Icons.storefront_outlined, 'Réservation de stands'),
      (Icons.qr_code_scanner, 'Contrôle d’accès QR'),
      (Icons.insights_outlined, 'Statistiques en direct'),
    ];
    return Container(
      color: Brand.b800,
      padding: const EdgeInsets.fromLTRB(16, 32, 16, 32),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Surtitre('Organisateurs', color: Brand.b200),
        const SizedBox(height: 8),
        Text('Vous organisez un salon, un festival ou un forum ?',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(color: Colors.white)),
        const SizedBox(height: 10),
        Text('Publiez votre événement et gérez tout depuis un seul espace, de l’inscription au contrôle des entrées.',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Brand.b100)),
        const SizedBox(height: 18),
        for (final a in atouts)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Row(children: [
              Icon(a.$1, color: Brand.gold, size: 20),
              const SizedBox(width: 12),
              Text(a.$2,
                  style: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600, color: Colors.white)),
            ]),
          ),
        const SizedBox(height: 14),
        SizedBox(
          width: double.infinity,
          child: FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.white, foregroundColor: Brand.b800),
            onPressed: () => context.push('/devenir-organisateur'),
            child: const Text('Devenir organisateur'),
          ),
        ),
      ]),
    );
  }

  String _compteCategorie(String nom) {
    final n = _aVenirTous.where((e) => e.categoryNom == nom).length;
    return n == 0 ? 'bientôt' : n == 1 ? '1 événement' : '$n événements';
  }

  IconData _iconeCategorie(String slug) {
    final s = slug.toLowerCase();
    if (s.contains('salon') || s.contains('foire')) return Icons.storefront_outlined;
    if (s.contains('festival') || s.contains('concert')) return Icons.music_note_outlined;
    if (s.contains('cinema') || s.contains('film')) return Icons.movie_outlined;
    if (s.contains('forum')) return Icons.groups_outlined;
    if (s.contains('conference')) return Icons.mic_none_outlined;
    if (s.contains('exposition')) return Icons.photo_library_outlined;
    if (s.contains('seminaire') || s.contains('formation')) return Icons.menu_book_outlined;
    if (s.contains('atelier')) return Icons.handyman_outlined;
    if (s.contains('institution')) return Icons.account_balance_outlined;
    return Icons.event_outlined;
  }
}

// ================== composants ==================

final _fr = DateFormat('EEE d MMM', 'fr');
final _frFin = DateFormat('d MMM yyyy', 'fr');
final _mois = DateFormat('MMM', 'fr');

String _dateCourte(EventSummary e) {
  final d = e.dateDebut?.toLocal();
  final f = e.dateFin?.toLocal();
  if (d == null) return '';
  final debut = _fr.format(d).replaceAll('.', '');
  if (f == null || (d.year == f.year && d.month == f.month && d.day == f.day)) {
    return '$debut · ${Fmt.time(d)}';
  }
  return '$debut → ${_frFin.format(f).replaceAll('.', '')}';
}

(String, Color, Color)? _statut(String s) => switch (s) {
      'INSCRIPTIONS_OUVERTES' => ('Inscriptions ouvertes', Brand.b50, Brand.b800),
      'EN_COURS' => ('En cours', Brand.b600, Colors.white),
      'INSCRIPTIONS_FERMEES' => ('Inscriptions closes', Brand.ocre50, Brand.ocre700),
      'PUBLIE' => ('Bientôt', Brand.bleu50, Brand.bleu700),
      'TERMINE' => ('Terminé', Brand.s100, Brand.s700),
      _ => null,
    };

class _StatutPill extends StatelessWidget {
  final String statut;
  const _StatutPill(this.statut);

  @override
  Widget build(BuildContext context) {
    final s = _statut(statut);
    if (s == null) return const SizedBox.shrink();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: s.$2, borderRadius: BorderRadius.circular(999)),
      child: Text(s.$1, style: TextStyle(color: s.$3, fontSize: 11.5, fontWeight: FontWeight.w600)),
    );
  }
}

class _FeaturedCard extends StatelessWidget {
  final EventSummary event;
  const _FeaturedCard({required this.event});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final d = event.dateDebut;
    final jours = d == null ? null : d.difference(DateTime.now()).inHours / 24;
    final place = [event.lieu, event.ville].where((e) => e != null && e.isNotEmpty).join(', ');
    return Card(
      clipBehavior: Clip.antiAlias,
      elevation: 0,
      shadowColor: Colors.black26,
      child: InkWell(
        onTap: () => context.push('/evenements/${event.slug}'),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(
            color: Brand.s900,
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: EventCover(nom: event.nom, url: event.coverUrl, fit: BoxFit.contain),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Wrap(spacing: 6, runSpacing: 6, children: [
                _StatutPill(event.statut),
                if (event.categoryNom != null)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(color: Brand.s100, borderRadius: BorderRadius.circular(999)),
                    child: Text(event.categoryNom!,
                        style: const TextStyle(color: Brand.s700, fontSize: 11.5, fontWeight: FontWeight.w600)),
                  ),
              ]),
              const SizedBox(height: 12),
              Text(event.nom, style: t.headlineMedium),
              if (event.descriptionCourte != null) ...[
                const SizedBox(height: 6),
                Text(event.descriptionCourte!, maxLines: 2, overflow: TextOverflow.ellipsis, style: t.bodyMedium),
              ],
              const SizedBox(height: 14),
              _Info(icon: Icons.calendar_today_outlined, text: Fmt.range(event.dateDebut, event.dateFin)),
              if (place.isNotEmpty) _Info(icon: Icons.place_outlined, text: place),
              if (event.organizerNom != null) _Info(icon: Icons.account_balance_outlined, text: 'Organisé par ${event.organizerNom}'),
              const SizedBox(height: 16),
              Row(children: [
                if (jours != null)
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(jours <= 0 ? 'STATUT' : 'COMMENCE DANS',
                          style: const TextStyle(
                              fontFamily: Brand.display, fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.8, color: Brand.s600)),
                      Text(jours <= 0 ? 'En cours' : jours <= 1 ? '1 jour' : '${jours.ceil()} jours',
                          style: const TextStyle(
                              fontFamily: Brand.display, fontSize: 24, fontWeight: FontWeight.w800, color: Brand.s900)),
                    ]),
                  ),
                FilledButton.icon(
                  onPressed: () => context.push('/evenements/${event.slug}?participer=1'),
                  icon: const Icon(Icons.confirmation_number_outlined, size: 18),
                  label: const Text('Réserver'),
                ),
              ]),
            ]),
          ),
        ]),
      ),
    );
  }
}

class _EventCard extends StatelessWidget {
  final EventSummary event;
  final bool passe;
  const _EventCard({required this.event, required this.passe});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final d = event.dateDebut?.toLocal();
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/evenements/${event.slug}'),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Stack(children: [
            AspectRatio(aspectRatio: 16 / 9, child: EventCover(nom: event.nom, url: event.coverUrl)),
            if (d != null)
              Positioned(
                top: 10,
                left: 10,
                child: Container(
                  width: 52,
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(6),
                    boxShadow: const [BoxShadow(color: Color(0x1F211D1A), blurRadius: 6, offset: Offset(0, 2))],
                  ),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(_mois.format(d).replaceAll('.', '').toUpperCase(),
                        style: const TextStyle(
                            fontFamily: Brand.display, fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.8, color: Brand.b700)),
                    Text(d.day.toString().padLeft(2, '0'),
                        style: const TextStyle(
                            fontFamily: Brand.display, fontSize: 22, fontWeight: FontWeight.w800, height: 1.1, color: Brand.s900)),
                  ]),
                ),
              ),
            Positioned(top: 10, right: 10, child: _StatutPill(event.statut)),
          ]),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text.rich(
                TextSpan(children: [
                  TextSpan(text: _dateCourte(event).toUpperCase()),
                  if (event.categoryNom != null)
                    TextSpan(text: '  ·  ${event.categoryNom!.toUpperCase()}', style: const TextStyle(color: Brand.s500)),
                ]),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    fontFamily: Brand.display, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.6, color: Brand.b700),
              ),
              const SizedBox(height: 6),
              Text(event.nom, maxLines: 2, overflow: TextOverflow.ellipsis, style: t.titleLarge),
              if (event.descriptionCourte != null) ...[
                const SizedBox(height: 4),
                Text(event.descriptionCourte!, maxLines: 2, overflow: TextOverflow.ellipsis, style: t.bodySmall),
              ],
              const SizedBox(height: 12),
              const Divider(height: 1),
              const SizedBox(height: 10),
              Row(children: [
                const Icon(Icons.place_outlined, size: 16, color: Brand.s500),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(event.ville ?? event.lieu ?? '—',
                      maxLines: 1, overflow: TextOverflow.ellipsis, style: t.bodySmall?.copyWith(color: Brand.s700)),
                ),
                Text(passe ? 'Voir' : 'Réserver',
                    style: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w700, color: Brand.b700)),
                const SizedBox(width: 4),
                const Icon(Icons.arrow_forward, size: 16, color: Brand.b700),
              ]),
            ]),
          ),
        ]),
      ),
    );
  }
}

class _Onglets extends StatelessWidget {
  final bool passes;
  final int nAVenir;
  final int nPasses;
  final ValueChanged<bool> onChanged;
  const _Onglets({required this.passes, required this.nAVenir, required this.nPasses, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    Widget onglet(String label, int n, bool actif, VoidCallback onTap) => Expanded(
          child: Semantics(
            selected: actif,
            button: true,
            child: GestureDetector(
              onTap: onTap,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 160),
                height: 40,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: actif ? Colors.white : Colors.transparent,
                  borderRadius: BorderRadius.circular(5),
                  boxShadow: actif ? const [BoxShadow(color: Color(0x14211D1A), blurRadius: 3, offset: Offset(0, 1))] : null,
                ),
                child: Text.rich(TextSpan(children: [
                  TextSpan(text: label),
                  TextSpan(text: '  $n', style: const TextStyle(color: Brand.s500)),
                ])),
              ),
            ),
          ),
        );
    return DefaultTextStyle(
      style: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600, fontSize: 14, color: Brand.s900),
      child: Container(
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(color: Brand.s100, borderRadius: BorderRadius.circular(6)),
        child: Row(children: [
          onglet('À venir', nAVenir, !passes, () => onChanged(false)),
          onglet('Passés', nPasses, passes, () => onChanged(true)),
        ]),
      ),
    );
  }
}

class _FiltreChip extends StatelessWidget {
  final String text;
  const _FiltreChip(this.text);

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(color: Brand.b50, borderRadius: BorderRadius.circular(999)),
        child: Text(text, style: const TextStyle(color: Brand.b800, fontSize: 13, fontWeight: FontWeight.w500)),
      );
}

class _PilluleClaire extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  const _PilluleClaire({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.transparent,
        shape: StadiumBorder(side: BorderSide(color: Colors.white.withValues(alpha: .28))),
        child: InkWell(
          customBorder: const StadiumBorder(),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(icon, size: 17, color: Colors.white),
              const SizedBox(width: 6),
              Text(label, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
            ]),
          ),
        ),
      );
}

class _Chiffre extends StatelessWidget {
  final String valeur;
  final String label;
  const _Chiffre({required this.valeur, required this.label});

  @override
  Widget build(BuildContext context) => Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 12, color: Brand.s400)),
          const SizedBox(height: 2),
          Text(valeur,
              style: const TextStyle(
                  fontFamily: Brand.display,
                  fontSize: 26,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                  fontFeatures: [FontFeature.tabularFigures()])),
        ]),
      );
}

class _Info extends StatelessWidget {
  final IconData icon;
  final String text;
  const _Info({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(
            width: 30,
            height: 30,
            decoration: BoxDecoration(color: Brand.b50, borderRadius: BorderRadius.circular(6)),
            child: Icon(icon, size: 16, color: Brand.b700),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 5),
              child: Text(text, style: const TextStyle(fontSize: 14, color: Brand.s800)),
            ),
          ),
        ]),
      );
}

class _Garantie extends StatelessWidget {
  final IconData icon;
  final String text;
  const _Garantie(this.icon, this.text);

  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 18, color: Brand.b600),
        const SizedBox(width: 6),
        Text(text, style: const TextStyle(fontSize: 13, color: Brand.s700)),
      ]);
}

class _Vide extends StatelessWidget {
  final IconData icon;
  final String titre;
  final String texte;
  final Widget? action;
  const _Vide({required this.icon, required this.titre, required this.texte, this.action});

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.symmetric(horizontal: 16),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 36),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: Brand.s300),
        ),
        child: Column(children: [
          Container(
            width: 56,
            height: 56,
            decoration: const BoxDecoration(color: Brand.s50, shape: BoxShape.circle),
            child: Icon(icon, color: Brand.s500, size: 28),
          ),
          const SizedBox(height: 14),
          Text(titre, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(texte, textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodySmall),
          if (action != null) ...[const SizedBox(height: 16), action!],
        ]),
      );
}

class _SkeletonCard extends StatelessWidget {
  const _SkeletonCard();

  @override
  Widget build(BuildContext context) => Card(
        clipBehavior: Clip.antiAlias,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const AspectRatio(aspectRatio: 16 / 9, child: ColoredBox(color: Brand.s100)),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Container(height: 10, width: 120, color: Brand.s100),
              const SizedBox(height: 10),
              Container(height: 18, width: 220, color: Brand.s100),
              const SizedBox(height: 10),
              Container(height: 10, width: 160, color: Brand.s100),
            ]),
          ),
        ]),
      );
}
