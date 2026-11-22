import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../core/brand.dart';
import '../../core/documents.dart';
import '../../core/faso.dart' show FasoTissage, Surtitre;
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import '../purchase/guest_gate.dart';
import '../shell/app_shell.dart';
import 'programme_chart.dart';

class _Bundle {
  final EventDetail event;
  final List<EventTicketType> tickets;
  final List<StandType> standTypes;
  _Bundle(this.event, this.tickets, this.standTypes);
}

class EventDetailScreen extends ConsumerStatefulWidget {
  final String slug;

  /// Arrivée depuis « S'inscrire et prendre un billet » : on descend au formulaire.
  final bool participer;
  const EventDetailScreen({super.key, required this.slug, this.participer = false});

  @override
  ConsumerState<EventDetailScreen> createState() => _EventDetailScreenState();
}

class _EventDetailScreenState extends ConsumerState<EventDetailScreen> {
  late Future<_Bundle> _future;
  final _participateKey = GlobalKey();
  bool _scrolled = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  void _scrollToParticipation() {
    if (!widget.participer || _scrolled) return;
    _scrolled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final ctx = _participateKey.currentContext;
      if (ctx != null) {
        Scrollable.ensureVisible(ctx,
            duration: const Duration(milliseconds: 450), curve: Curves.easeInOut);
      }
    });
  }

  Future<_Bundle> _load() async {
    final repo = ref.read(eventsRepositoryProvider);
    final event = await repo.bySlug(widget.slug);
    final tickets = await repo.tickets(widget.slug).catchError((_) => <EventTicketType>[]);
    final stands = event.standsActifs
        ? await repo.standTypes(widget.slug).catchError((_) => <StandType>[])
        : <StandType>[];
    return _Bundle(event, tickets, stands);
  }

  void _refresh() => setState(() => _future = _load());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Brand.s900,
        foregroundColor: Colors.white,
        shape: const Border(),
        title: const Text('Événement', style: TextStyle(color: Colors.white)),
      ),
      body: FutureView<_Bundle>(
        future: _future,
        onRetry: _refresh,
        builder: (b) {
          _scrollToParticipation();
          final e = b.event;
          final auto = _autoTickets(e);
          final vente = b.tickets.where((t) => _enBilletterie(t, auto)).toList();
          return Column(children: [
            Expanded(
              child: _Body(bundle: b, onChanged: _refresh, participateKey: _participateKey),
            ),
            if (e.inscriptionsOuvertes)
              _BarreReservation(
                prix: _prixApartir(vente),
                label: vente.isNotEmpty && vente.every((t) => t.gratuit) ? 'Obtenir mon billet' : 'Réserver',
                onTap: () {
                  final ctx = _participateKey.currentContext;
                  if (ctx != null) {
                    Scrollable.ensureVisible(ctx,
                        duration: const Duration(milliseconds: 450), curve: Curves.easeOutCubic);
                  }
                },
              ),
          ]);
        },
      ),
    );
  }
}

/// Les billets gratuits d'activité se prennent depuis le programme (« Participer ») :
/// on ne les répète pas dans la billetterie.
bool _enBilletterie(EventTicketType t, Set<String> auto) => t.enVente && !auto.contains(t.id);

/// Billets automatiques des activités gratuites (« Accès — … ») : ils se prennent
/// depuis le programme via « Réserver cette session ». Les pass multi-activités
/// créés par l'organisateur restent, eux, dans la billetterie.
Set<String> _autoTickets(EventDetail e) => e.programme.map((a) => a.freeTicketId).whereType<String>().toSet();

String _prixApartir(List<EventTicketType> t) {
  if (t.isEmpty) return 'Inscription libre';
  final payants = t.where((x) => !x.gratuit).toList();
  if (payants.isEmpty) return 'Gratuit';
  payants.sort((a, b) => a.prixMontant.compareTo(b.prixMontant));
  return t.length > 1 ? 'Dès ${payants.first.prixFormatte}' : payants.first.prixFormatte;
}

/// Barre de réservation fixée en bas de l'écran.
class _BarreReservation extends StatelessWidget {
  final String prix;
  final String label;
  final VoidCallback onTap;
  const _BarreReservation({required this.prix, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: Brand.s200)),
          boxShadow: [BoxShadow(color: Color(0x14211D1A), blurRadius: 16, offset: Offset(0, -4))],
        ),
        padding: EdgeInsets.fromLTRB(16, 10, 16, 10 + MediaQuery.of(context).padding.bottom),
        child: Row(children: [
          Expanded(
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('BILLETTERIE',
                  style: TextStyle(
                      fontFamily: Brand.display, fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.8, color: Brand.s600)),
              Text(prix,
                  style: const TextStyle(fontFamily: Brand.display, fontSize: 18, fontWeight: FontWeight.w800, color: Brand.s900)),
            ]),
          ),
          FilledButton.icon(
            onPressed: onTap,
            icon: const Icon(Icons.confirmation_number_outlined, size: 18),
            label: Text(label),
          ),
        ]),
      );
}

class _Body extends ConsumerWidget {
  final _Bundle bundle;
  final VoidCallback onChanged;
  final GlobalKey participateKey;
  const _Body(
      {required this.bundle,
      required this.onChanged,
      required this.participateKey});

  Future<void> _ouvrir(String url) async {
    await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final e = bundle.event;
    final t = Theme.of(context).textTheme;
    final auto = _autoTickets(e);
    final payantTickets = bundle.tickets.where((t) => _enBilletterie(t, auto)).toList();
    // Pass multi-activités : pour afficher « Inclus dans : Pass Conférences » dans le programme.
    final passes = payantTickets.where((t) => t.portee == 'ACTIVITE').toList();
    List<String> passesPour(String activityId) =>
        passes.where((p) => p.activiteIds.contains(activityId)).map((p) => p.nom).toList();
    final canRegister = e.inscriptionsOuvertes;
    final lieu = [e.lieu, e.adresse, e.ville].where((x) => x != null && x.isNotEmpty).join(', ');

    return ListView(
      padding: EdgeInsets.zero,
      // Tout est construit d'avance pour que le défilement vers la billetterie fonctionne.
      cacheExtent: 100000,
      children: [
        // ============ BANDEAU ============
        Container(
          color: Brand.s900,
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: ColoredBox(
                  color: Colors.black26,
                  child: AspectRatio(
                    aspectRatio: 16 / 9,
                    child: EventCover(nom: e.nom, url: e.coverUrl, fit: BoxFit.contain),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 18, 16, 22),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Wrap(spacing: 6, runSpacing: 6, children: [
                  StatusChip(e.statut),
                  if (e.categoryNom != null)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: .1), borderRadius: BorderRadius.circular(999)),
                      child: Text(e.categoryNom!,
                          style: const TextStyle(color: Brand.s200, fontSize: 12, fontWeight: FontWeight.w600)),
                    ),
                ]),
                const SizedBox(height: 12),
                Text(e.nom, style: t.headlineLarge?.copyWith(color: Colors.white)),
                if (e.descriptionCourte != null) ...[
                  const SizedBox(height: 8),
                  Text(e.descriptionCourte!, style: t.bodyLarge?.copyWith(color: Brand.s300)),
                ],
                const SizedBox(height: 16),
                _HeroInfo(Icons.calendar_today_outlined, Fmt.range(e.dateDebut, e.dateFin)),
                if (lieu.isNotEmpty) _HeroInfo(Icons.place_outlined, lieu),
                if (e.organizerNom != null) _HeroInfo(Icons.account_balance_outlined, 'Organisé par ${e.organizerNom}'),
              ]),
            ),
            const FasoTissage(),
          ]),
        ),

        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // --- À propos ---
              if ((e.descriptionDetaillee ?? e.descriptionCourte) != null) ...[
                const _Section('À propos'),
                Text(e.descriptionDetaillee ?? e.descriptionCourte!, style: t.bodyLarge),
              ],

              if (e.conditionsParticipation != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Brand.bleu50,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Brand.bleu100),
                  ),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Icon(Icons.info_outline, color: Brand.bleu700, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text.rich(TextSpan(children: [
                        const TextSpan(text: 'Conditions de participation. ', style: TextStyle(fontWeight: FontWeight.w700)),
                        TextSpan(text: e.conditionsParticipation!),
                      ]), style: const TextStyle(color: Brand.bleu700, fontSize: 14, height: 1.5)),
                    ),
                  ]),
                ),
              ],

              // --- Programme / activités ---
              if (e.hasActivities && e.programme.isNotEmpty) ...[
                const SizedBox(height: 32),
                _Section('Programme', sousTitre: '${e.programme.length} activité${e.programme.length > 1 ? 's' : ''}'),
                ProgrammeChart(
                  title: e.nom,
                  subtitle: Fmt.rangeShort(e.dateDebut, e.dateFin),
                  activities: e.programme,
                  cardBuilder: (a) => _ActivityTile(a, slug: e.slug, inclusDans: passesPour(a.id)),
                ),
              ],

              // --- Intervenants ---
              if (e.intervenants.isNotEmpty) ...[
                const SizedBox(height: 32),
                const _Section('Intervenants'),
                ...e.intervenants.map((s) {
                  final photo = resolveMediaUrl(s.photoUrl);
                  final init = s.nom.trim().split(RegExp(r'\s+')).take(2).map((m) => m.isEmpty ? '' : m[0].toUpperCase()).join();
                  return Card(
                    margin: const EdgeInsets.only(bottom: 10),
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Row(children: [
                        CircleAvatar(
                          radius: 26,
                          backgroundColor: Brand.b50,
                          backgroundImage: photo != null ? NetworkImage(photo) : null,
                          child: photo == null
                              ? Text(init,
                                  style: const TextStyle(
                                      fontFamily: Brand.display, fontWeight: FontWeight.w700, color: Brand.b700, fontSize: 16))
                              : null,
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(s.nom, style: t.titleMedium),
                            if (s.titre != null || s.organisation != null)
                              Text([s.titre, s.organisation].where((x) => x != null).join(' · '), style: t.bodySmall),
                          ]),
                        ),
                      ]),
                    ),
                  );
                }),
              ],

              // --- Partenaires ---
              if (e.partenaires.isNotEmpty) ...[
                const SizedBox(height: 32),
                const _Section('Partenaires'),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: e.partenaires.map((p) {
                    final logo = resolveMediaUrl(p.logoUrl);
                    return Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        border: Border.all(color: Brand.s200),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(mainAxisSize: MainAxisSize.min, children: [
                        if (logo != null) ...[
                          Image.network(logo, height: 24, errorBuilder: (_, __, ___) => const SizedBox.shrink()),
                          const SizedBox(width: 8),
                        ],
                        Text(p.nom, style: t.bodySmall?.copyWith(color: Brand.s800, fontWeight: FontWeight.w500)),
                      ]),
                    );
                  }).toList(),
                ),
              ],

              // --- Billetterie ---
              SizedBox(key: participateKey, height: 32),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Brand.s200),
                  boxShadow: const [BoxShadow(color: Color(0x14211D1A), blurRadius: 12, offset: Offset(0, 4))],
                ),
                clipBehavior: Clip.antiAlias,
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Container(
                    width: double.infinity,
                    color: Brand.s50,
                    padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Surtitre('Billetterie', color: Brand.s600),
                      const SizedBox(height: 2),
                      Text(_prixApartir(payantTickets),
                          style: const TextStyle(
                              fontFamily: Brand.display, fontSize: 22, fontWeight: FontWeight.w800, color: Brand.s900)),
                    ]),
                  ),
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                      if (payantTickets.isNotEmpty && canRegister)
                        _TicketPurchaseSection(event: e, tickets: payantTickets)
                      else if (payantTickets.isNotEmpty)
                        ...payantTickets.map((t) => Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: Row(children: [
                                Expanded(child: Text(t.nom, style: const TextStyle(fontWeight: FontWeight.w600))),
                                Text(t.gratuit ? 'Gratuit' : t.prixFormatte,
                                    style: const TextStyle(fontWeight: FontWeight.w700, color: Brand.s900)),
                              ]),
                            )),
                      if (canRegister) ...[
                        if (payantTickets.isNotEmpty) const SizedBox(height: 10),
                        OutlinedButton.icon(
                          onPressed: () => context.push('/evenements/${e.slug}/inscription'),
                          icon: const Icon(Icons.how_to_reg_outlined),
                          label: const Text('S’inscrire à l’événement'),
                        ),
                      ] else
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(color: Brand.s50, borderRadius: BorderRadius.circular(6)),
                          child: Text(
                              e.termine
                                  ? 'Cet événement est terminé. Les inscriptions sont closes.'
                                  : 'Les inscriptions ne sont pas encore ouvertes pour cet événement.',
                              style: t.bodyMedium),
                        ),
                    ]),
                  ),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
                    decoration: const BoxDecoration(border: Border(top: BorderSide(color: Brand.s200)), color: Brand.s25),
                    child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      _Garantie(Icons.verified_user_outlined, 'Paiement en ligne sécurisé'),
                      _Garantie(Icons.qr_code_2, 'Billet électronique avec QR code'),
                      _Garantie(Icons.print_disabled_outlined, 'Pas besoin d’imprimer'),
                    ]),
                  ),
                ]),
              ),

              // --- Stands ---
              if (e.standsActifs && bundle.standTypes.isNotEmpty) ...[
                const SizedBox(height: 32),
                const _Section('Stands', sousTitre: 'Exposez votre activité pendant l’événement.'),
                ...bundle.standTypes.map((s) => Card(
                      margin: const EdgeInsets.only(bottom: 10),
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Row(children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(color: Brand.b50, borderRadius: BorderRadius.circular(6)),
                            child: const Icon(Icons.storefront_outlined, color: Brand.b700, size: 20),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              Text(s.nom, style: t.titleMedium),
                              Text([
                                if (s.dimensions != null) s.dimensions!,
                                '${s.quantiteRestante} disponible${s.quantiteRestante > 1 ? 's' : ''}',
                              ].join(' · '), style: t.bodySmall),
                            ]),
                          ),
                          Text(Fmt.price(s.prixMontant, s.devise),
                              style: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w700, color: Brand.s900)),
                        ]),
                      ),
                    )),
                if (canRegister)
                  OutlinedButton.icon(
                    onPressed: () => requireAuthOr(context, ref, () => context.push('/evenements/${e.slug}/stands')),
                    icon: const Icon(Icons.storefront_outlined),
                    label: const Text('Réserver un stand'),
                  ),
              ],

              // --- Infos pratiques ---
              const SizedBox(height: 32),
              const _Section('Informations pratiques'),
              _InfoCard(
                icon: Icons.calendar_today_outlined,
                label: 'Date et heure',
                value: Fmt.range(e.dateDebut, e.dateFin),
                extra: e.inscriptionFin != null ? 'Inscriptions jusqu’au ${Fmt.date(e.inscriptionFin)}' : null,
              ),
              if (lieu.isNotEmpty)
                _InfoCard(
                  icon: Icons.place_outlined,
                  label: 'Lieu',
                  value: lieu,
                  action: 'Itinéraire',
                  onAction: () => _ouvrir(
                      'https://www.google.com/maps/search/?api=1&query=${Uri.encodeComponent(lieu)}'),
                ),
              if (e.contactEmail != null)
                _InfoCard(
                  icon: Icons.mail_outline,
                  label: 'E-mail de contact',
                  value: e.contactEmail!,
                  action: 'Écrire',
                  onAction: () => _ouvrir('mailto:${e.contactEmail}'),
                ),
              if (e.contactTelephone != null)
                _InfoCard(
                  icon: Icons.phone_outlined,
                  label: 'Téléphone',
                  value: e.contactTelephone!,
                  action: 'Appeler',
                  onAction: () => _ouvrir('tel:${e.contactTelephone!.replaceAll(' ', '')}'),
                ),
              if (e.siteWeb != null)
                _InfoCard(
                  icon: Icons.language,
                  label: 'Site web',
                  value: e.siteWeb!,
                  action: 'Ouvrir',
                  onAction: () => _ouvrir(e.siteWeb!),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  final String? sousTitre;
  const _Section(this.title, {this.sousTitre});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: Theme.of(context).textTheme.headlineSmall),
          if (sousTitre != null) ...[
            const SizedBox(height: 2),
            Text(sousTitre!, style: Theme.of(context).textTheme.bodySmall),
          ],
        ]),
      );
}

class _HeroInfo extends StatelessWidget {
  final IconData icon;
  final String text;
  const _HeroInfo(this.icon, this.text);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, size: 19, color: Brand.gold),
          const SizedBox(width: 12),
          Expanded(child: Text(text, style: const TextStyle(color: Brand.s100, fontSize: 15, height: 1.4))),
        ]),
      );
}

class _Garantie extends StatelessWidget {
  final IconData icon;
  final String text;
  const _Garantie(this.icon, this.text);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Icon(icon, size: 16, color: Brand.b600),
          const SizedBox(width: 8),
          Text(text, style: const TextStyle(fontSize: 12.5, color: Brand.s700)),
        ]),
      );
}

class _InfoCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final String? extra;
  final String? action;
  final VoidCallback? onAction;
  const _InfoCard({required this.icon, required this.label, required this.value, this.extra, this.action, this.onAction});

  @override
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.only(bottom: 10),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
          child: Row(children: [
            Icon(icon, color: Brand.b700, size: 22),
            const SizedBox(width: 14),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(label, style: Theme.of(context).textTheme.bodySmall),
                Text(value, style: const TextStyle(fontWeight: FontWeight.w600, color: Brand.s900, fontSize: 14.5)),
                if (extra != null) Text(extra!, style: Theme.of(context).textTheme.bodySmall),
              ]),
            ),
            if (action != null) TextButton(onPressed: onAction, child: Text(action!)),
          ]),
        ),
      );
}

class _ActivityTile extends ConsumerStatefulWidget {
  final Activity a;
  final String slug;
  final List<String> inclusDans;
  const _ActivityTile(this.a, {required this.slug, this.inclusDans = const []});
  @override
  ConsumerState<_ActivityTile> createState() => _ActivityTileState();
}

class _ActivityTileState extends ConsumerState<_ActivityTile> {
  bool _busy = false;
  bool _joined = false;
  String? _error;

  Future<void> _participate() async {
    if (!await GuestGate.ensureSession(context, ref)) return;
    if (!mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(ticketsRepositoryProvider).attendActivity(widget.a.id);
      if (mounted) setState(() => _joined = true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.a;
    final type = activityTypeStyle(a.typeActivite);
    final slot = a.dateFin != null
        ? '${Fmt.time(a.dateDebut)} – ${Fmt.time(a.dateFin)}'
        : Fmt.time(a.dateDebut);
    final small = Theme.of(context).textTheme.bodySmall;
    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE2DDD6)),
        boxShadow: const [
          BoxShadow(color: Color(0x0F211D1A), blurRadius: 4, offset: Offset(0, 1)),
        ],
      ),
      child: Container(
        decoration: BoxDecoration(
          border: Border(left: BorderSide(color: type.color, width: 4)),
        ),
        child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (a.imageUrl != null)
            AspectRatio(aspectRatio: 16 / 6, child: RemoteImage(url: a.imageUrl)),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  if (slot.isNotEmpty)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: type.color.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(slot,
                          style: TextStyle(
                              color: type.color, fontSize: 12, fontWeight: FontWeight.w700)),
                    ),
                  const Spacer(),
                  Text(type.label.toUpperCase(),
                      style: const TextStyle(
                          fontSize: 10,
                          letterSpacing: 0.8,
                          fontWeight: FontWeight.w600,
                          color: Color(0xFFA9A199))),
                ]),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: Text(a.titre,
                          style: const TextStyle(fontWeight: FontWeight.w600)),
                    ),
                    if (a.gratuit)
                      const _AccessChip('Gratuit', Color(0xFF00843B))
                    else if (a.payant)
                      const _AccessChip('Payant', Color(0xFFB07800)),
                  ],
                ),
                const SizedBox(height: 4),
                if (a.salle != null || a.lieu != null)
                  Text('Lieu : ${a.salle ?? a.lieu}', style: small),
                if (a.intervenant != null)
                  Text('Intervenant : ${a.intervenant}', style: small),
                if (a.moderateur != null)
                  Text('Modérateur : ${a.moderateur}', style: small),
                if (a.description != null) ...[
                  const SizedBox(height: 6),
                  Text(a.description!,
                      style: Theme.of(context).textTheme.bodySmall),
                ],
                if (a.gratuit) ...[
                  const SizedBox(height: 8),
                  if (_joined)
                    Text('Vous participez — billet dans « Mes billets ».',
                        style: Theme.of(context)
                            .textTheme
                            .bodySmall
                            ?.copyWith(color: const Color(0xFF00843B)))
                  else
                    OutlinedButton(
                      onPressed: _busy ? null : _participate,
                      child: Text(_busy ? 'Un instant…' : 'Réserver cette session (gratuit)'),
                    ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(_error!,
                          style: const TextStyle(color: Color(0xFFA2040E), fontSize: 12)),
                    ),
                ] else if (a.payant) ...[
                  const SizedBox(height: 6),
                  Text('Billet requis — voir la billetterie ci-dessous.',
                      style: Theme.of(context).textTheme.bodySmall),
                ],
                if (widget.inclusDans.isNotEmpty) ...[
                  const SizedBox(height: 6),
                  Row(children: [
                    const Icon(Icons.confirmation_number_outlined, size: 14, color: Color(0xFF026C31)),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text('Inclus dans : ${widget.inclusDans.join(', ')}',
                          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF026C31))),
                    ),
                  ]),
                ],
              ],
            ),
          ),
        ],
        ),
      ),
    );
  }
}

class _AccessChip extends StatelessWidget {
  final String label;
  final Color color;
  const _AccessChip(this.label, this.color);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(label,
            style: TextStyle(
                color: color, fontSize: 11, fontWeight: FontWeight.w600)),
      );
}

/// Buy tickets right on the event page — no separate screen. A single
/// category is auto-selected (and its quantity hidden if free, since a free
/// category is capped at 1 per person); with several categories, the list
/// below acts as the picker. On success: free tickets are issued and their
/// PDF downloaded immediately; paid ones go through the payment screen,
/// which downloads them automatically once payment is confirmed.
class _TicketPurchaseSection extends ConsumerStatefulWidget {
  final EventDetail event;
  final List<EventTicketType> tickets;
  const _TicketPurchaseSection({required this.event, required this.tickets});

  @override
  ConsumerState<_TicketPurchaseSection> createState() =>
      _TicketPurchaseSectionState();
}

class _TicketPurchaseSectionState
    extends ConsumerState<_TicketPurchaseSection> {
  final Map<String, int> _qty = {};
  bool _submitting = false;
  String? _error;
  List<Ticket>? _issued;

  bool get _single => widget.tickets.length == 1;
  bool get _singleFree => _single && widget.tickets.first.gratuit;

  @override
  void initState() {
    super.initState();
    if (_single) _qty[widget.tickets.first.id] = 1;
  }

  num get _total {
    num sum = 0;
    for (final t in widget.tickets) {
      sum += t.prixMontant * (_qty[t.id] ?? 0);
    }
    return sum;
  }

  int get _count => _qty.values.fold(0, (a, b) => a + b);

  /// The organizer opted out of a form for this free category: only a phone
  /// prompt (or nothing at all if already signed in) stands between a scan
  /// and the ticket.
  bool get _noForm => _singleFree && !widget.tickets.first.formulaireRequis;

  Future<String?> _promptPhone() {
    String phone = '';
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Votre téléphone'),
        content: PhoneField(
          required: true,
          onChanged: (v) => phone = v,
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Annuler')),
          FilledButton(
            onPressed: () {
              if (phone.trim().isEmpty) return;
              Navigator.pop(ctx, phone.trim());
            },
            child: const Text('Continuer'),
          ),
        ],
      ),
    );
  }

  Future<void> _submit() async {
    final lignes = {
      for (final e in _qty.entries)
        if (e.value > 0) e.key: e.value,
    };
    if (lignes.isEmpty) return;

    final signedIn = ref.read(authControllerProvider).valueOrNull != null;
    String? quickPhone;
    if (!signedIn) {
      if (_noForm) {
        quickPhone = await _promptPhone();
        if (quickPhone == null) return; // cancelled
      } else if (!await GuestGate.ensureSession(context, ref)) {
        return;
      }
    }
    if (!mounted) return;

    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      if (quickPhone != null) {
        await ref.read(authControllerProvider.notifier).guestSessionQuick(quickPhone);
      }
      final order = await ref
          .read(ticketsRepositoryProvider)
          .createOrder(eventId: widget.event.id, lignes: lignes);
      if (!mounted) return;
      if (order.montantTotal == 0) {
        await _downloadIssued(order.reference);
      } else {
        context.push('/paiement/TICKET_ORDER/${order.id}');
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _downloadIssued(String orderReference) async {
    final all = await ref.read(ticketsRepositoryProvider).myTickets();
    final mine =
        all.where((t) => t.orderReference == orderReference).toList();
    if (mounted) setState(() => _issued = mine);
    final api = ref.read(apiClientProvider);
    for (final t in mine) {
      try {
        await fetchAndPresentDocument(api,
            path: '/api/tickets/${t.id}/pdf',
            filename: 'billet-${t.numero}.pdf');
      } catch (_) {
        // best-effort auto-download — the button below covers a failure
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_issued != null) {
      return Card(
        color: const Color(0xFFECF7F0),
        margin: const EdgeInsets.only(bottom: 8),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Billet obtenu',
                  style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF026C31))),
              const SizedBox(height: 4),
              Text(
                'Téléchargé automatiquement. Pas besoin de l\'imprimer, la '
                'version numérique suffit.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              for (final t in _issued!)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Row(
                    children: [
                      Expanded(child: Text('Billet ${t.numero}')),
                      TextButton(
                        onPressed: () => fetchAndPresentDocument(
                          ref.read(apiClientProvider),
                          path: '/api/tickets/${t.id}/pdf',
                          filename: 'billet-${t.numero}.pdf',
                        ),
                        child: const Text('Retélécharger'),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_singleFree)
          Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(widget.tickets.first.nom,
                      style: const TextStyle(fontWeight: FontWeight.w600)),
                  const SizedBox(height: 2),
                  const Text('Gratuit'),
                  if (_noForm) ...[
                    const SizedBox(height: 4),
                    Text(
                      'Juste votre téléphone suffit — pas de compte, pas '
                      'd\'autre champ à remplir.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ],
              ),
            ),
          )
        else
          ...widget.tickets.map((t) => _TicketRow(
                ticket: t,
                quantity: _qty[t.id] ?? 0,
                onChanged: (v) => setState(() => _qty[t.id] = v),
              )),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(_error!, style: const TextStyle(color: Color(0xFFA2040E))),
          ),
        FilledButton(
          onPressed: _submitting || _count == 0 ? null : _submit,
          child: Text(_submitting
              ? 'Un instant…'
              : _singleFree
                  ? 'Obtenir mon billet (Gratuit)'
                  : _count == 0
                      ? 'Choisissez vos billets'
                      : 'Réserver $_count billet${_count > 1 ? 's' : ''} · ${Fmt.price(_total)}'),
        ),
      ],
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
              Text(
                  '${ticket.activites.length} session${ticket.activites.length > 1 ? 's incluses' : ' incluse'}'
                  '${ticket.description == null ? ' : ${ticket.activites.join(' · ')}' : ''}',
                  style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: Color(0xFF026C31))),
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
