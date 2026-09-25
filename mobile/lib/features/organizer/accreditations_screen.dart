import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/documents.dart';
import '../../core/manage_kit.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

enum _Show { actifs, revoques, tous }

/// Colour of a badge by role, so a glance at the list tells speakers from press from staff.
KitTone _roleTone(String fonction) => switch (fonction) {
      'CONFERENCIER' || 'MODERATEUR' || 'PANELISTE' || 'MAITRE_CEREMONIE' => KitTone.violet,
      'EXPOSANT' => KitTone.amber,
      'PRESSE' => KitTone.blue,
      'STAFF' => KitTone.green,
      _ => KitTone.slate,
    };

class AccreditationsScreen extends ConsumerStatefulWidget {
  final String eventId;
  const AccreditationsScreen({super.key, required this.eventId});

  @override
  ConsumerState<AccreditationsScreen> createState() =>
      _AccreditationsScreenState();
}

class _AccreditationsScreenState extends ConsumerState<AccreditationsScreen> {
  late Future<List<Accreditation>> _future;
  final _search = TextEditingController();
  String _query = '';
  _Show _show = _Show.actifs;
  String? _busyId;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<List<Accreditation>> _load() =>
      ref.read(organizerEventsRepositoryProvider).accreditations(widget.eventId);

  void _refresh() => setState(() => _future = _load());

  Future<void> _add() async {
    final activities = await ref
        .read(organizerEventsRepositoryProvider)
        .activities(widget.eventId)
        .catchError((_) => <Activity>[]);
    if (!mounted) return;
    final created = await showEditorSheet<bool>(
      context,
      (_) => _AccreditationSheet(
        eventId: widget.eventId,
        activities: activities,
      ),
    );
    if (created == true) _refresh();
  }

  Future<void> _revoke(Accreditation a) async {
    final ok = await confirmAction(
      context,
      title: 'Révoquer le badge ?',
      message: '${a.personneNom} — ${a.fonctionLibelle}. '
          "Le QR ne sera plus valable à l'entrée.",
      confirmLabel: 'Révoquer',
      destructive: true,
    );
    if (!ok) return;
    try {
      await ref
          .read(organizerEventsRepositoryProvider)
          .revokeAccreditation(a.id);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  Future<void> _badge(Accreditation a) async {
    setState(() => _busyId = a.id);
    try {
      final outcome = await fetchAndPresentDocument(
        ref.read(apiClientProvider),
        path: a.badgePdfUrl,
        filename: 'badge-${a.numero}.pdf',
      );
      if (mounted && outcome == DocOutcome.downloaded) {
        showSnack(context, 'Téléchargement du badge lancé.');
      } else if (mounted && outcome == DocOutcome.savedNoViewer) {
        showSnack(context, "PDF enregistré ; aucune application pour l'ouvrir.",
            error: true);
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Badge indisponible : $e', error: true);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  bool _matches(Accreditation a) {
    if (_show == _Show.actifs && !a.active) return false;
    if (_show == _Show.revoques && a.active) return false;
    if (_query.isEmpty) return true;
    final hay = [a.personneNom, a.organisation, a.fonctionLibelle, a.numero, a.activiteNom]
        .whereType<String>()
        .join(' ')
        .toLowerCase();
    return hay.contains(_query);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Accréditations')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _add,
        icon: const Icon(Icons.add),
        label: const Text('Badge'),
      ),
      body: FutureView<List<Accreditation>>(
        future: _future,
        onRetry: _refresh,
        builder: (list) {
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.badge_outlined,
              title: 'Aucune accréditation',
              subtitle:
                  'Délivrez des badges nominatifs (conférencier, exposant, presse, '
                  "staff…) pour une activité ou pour tout l'événement.",
              action: FilledButton.icon(
                onPressed: _add,
                icon: const Icon(Icons.add),
                label: const Text('Délivrer un badge'),
              ),
            );
          }
          final actifs = list.where((a) => a.active).length;
          final visible = list.where(_matches).toList();
          return MaxWidth(
            child: Column(children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                child: TextField(
                  controller: _search,
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: 'Rechercher un nom, une organisation…',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _query.isEmpty
                        ? null
                        : IconButton(
                            icon: const Icon(Icons.close),
                            onPressed: () {
                              _search.clear();
                              setState(() => _query = '');
                            },
                          ),
                  ),
                  onChanged: (v) => setState(() => _query = v.trim().toLowerCase()),
                ),
              ),
              FilterPills<_Show>(
                items: [
                  (value: _Show.actifs, label: 'Actifs', count: actifs),
                  if (list.length > actifs)
                    (value: _Show.revoques, label: 'Révoqués', count: list.length - actifs),
                  (value: _Show.tous, label: 'Tous', count: list.length),
                ],
                selected: _show,
                onSelected: (v) => setState(() => _show = v),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: RefreshIndicator(
                  onRefresh: () async => _refresh(),
                  child: visible.isEmpty
                      ? ListView(children: const [
                          SizedBox(height: 60),
                          EmptyState(
                            icon: Icons.search_off,
                            title: 'Aucun badge trouvé',
                            subtitle: 'Essayez un autre nom ou un autre filtre.',
                          ),
                        ])
                      : ListView.builder(
                          padding: const EdgeInsets.fromLTRB(16, 4, 16, 96),
                          itemCount: visible.length,
                          itemBuilder: (_, i) => _badgeCard(visible[i]),
                        ),
                ),
              ),
            ]),
          );
        },
      ),
    );
  }

  Widget _badgeCard(Accreditation a) {
    final tone = a.active ? _roleTone(a.fonction) : KitTone.slate;
    return ItemCard(
      leading: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(color: tone.bg, borderRadius: BorderRadius.circular(22)),
        alignment: Alignment.center,
        child: Text(
          a.personneNom.isEmpty ? '?' : a.personneNom.substring(0, 1).toUpperCase(),
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: tone.fg),
        ),
      ),
      title: a.personneNom,
      subtitle: [a.organisation, 'N° ${a.numero}']
          .where((s) => s != null && s.isNotEmpty)
          .join(' · '),
      onTap: a.active ? () => _badge(a) : null,
      chips: [
        MiniChip(a.fonctionLibelle, tone: tone),
        MiniChip(a.activiteNom ?? "Tout l'événement", icon: Icons.event_outlined),
        if (!a.active) const MiniChip('Révoqué', tone: KitTone.red),
      ],
      footer: _busyId == a.id
          ? ClipRRect(
              borderRadius: BorderRadius.circular(99),
              child: const LinearProgressIndicator(minHeight: 4),
            )
          : null,
      actions: [
        if (a.active) ...[
          ItemAction('Badge PDF', Icons.picture_as_pdf_outlined, () => _badge(a)),
          ItemAction('Révoquer', Icons.block, () => _revoke(a), destructive: true),
        ] else
          ItemAction('Badge PDF', Icons.picture_as_pdf_outlined, () => _badge(a)),
      ],
    );
  }
}

class _AccreditationSheet extends ConsumerStatefulWidget {
  final String eventId;
  final List<Activity> activities;
  const _AccreditationSheet({required this.eventId, required this.activities});

  @override
  ConsumerState<_AccreditationSheet> createState() => _AccreditationSheetState();
}

class _AccreditationSheetState extends ConsumerState<_AccreditationSheet> {
  final _nom = TextEditingController();
  final _organisation = TextEditingController();
  final _email = TextEditingController();
  final _fonctionLibre = TextEditingController();
  String _fonction = 'CONFERENCIER';
  String? _activityId;
  bool _saving = false;

  @override
  void dispose() {
    _nom.dispose();
    _organisation.dispose();
    _email.dispose();
    _fonctionLibre.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_nom.text.trim().isEmpty) {
      showSnack(context, 'Nom requis.', error: true);
      return;
    }
    if (_fonction == 'AUTRE' && _fonctionLibre.text.trim().isEmpty) {
      showSnack(context, 'Précisez la fonction.', error: true);
      return;
    }
    setState(() => _saving = true);
    try {
      await ref.read(organizerEventsRepositoryProvider).issueAccreditation(
        widget.eventId,
        {
          'personneNom': _nom.text.trim(),
          if (_organisation.text.trim().isNotEmpty)
            'organisation': _organisation.text.trim(),
          if (_email.text.trim().isNotEmpty) 'personneEmail': _email.text.trim(),
          'fonction': _fonction,
          if (_fonction == 'AUTRE') 'fonctionLibre': _fonctionLibre.text.trim(),
          if (_activityId != null) 'activityId': _activityId,
        },
      );
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _saving = false);
        showSnack(context, e.message, error: true);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SheetScaffold(
      title: 'Nouveau badge',
      subtitle: 'Accréditation nominative avec QR et PDF',
      icon: Icons.badge_outlined,
      tone: KitTone.violet,
      action: FilledButton(
        onPressed: _saving ? null : _save,
        child: Text(_saving ? 'Création…' : 'Délivrer le badge'),
      ),
      children: [
        TextField(
          controller: _nom,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(labelText: 'Nom de la personne *'),
        ),
        TextField(
          controller: _organisation,
          decoration: const InputDecoration(labelText: 'Organisation'),
        ),
        TextField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          decoration: const InputDecoration(labelText: 'E-mail (optionnel)'),
        ),
        DropdownButtonFormField<String>(
          value: _fonction,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Fonction'),
          items: kAccreditationRoles.entries
              .map((e) => DropdownMenuItem(value: e.key, child: Text(e.value)))
              .toList(),
          onChanged: (v) => setState(() => _fonction = v ?? 'CONFERENCIER'),
        ),
        if (_fonction == 'AUTRE')
          TextField(
            controller: _fonctionLibre,
            decoration:
                const InputDecoration(labelText: 'Fonction (texte libre) *'),
          ),
        DropdownButtonFormField<String?>(
          value: _activityId,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Portée'),
          items: [
            const DropdownMenuItem(value: null, child: Text('Toutes les activités')),
            ...widget.activities.map((a) =>
                DropdownMenuItem(value: a.id, child: Text(a.titre, overflow: TextOverflow.ellipsis))),
          ],
          onChanged: (v) => setState(() => _activityId = v),
        ),
        const InfoBanner(
          "Le badge (QR + PDF) est disponible dans la liste dès qu'il est délivré : "
          "ouvrez-le pour le télécharger ou l'imprimer.",
        ),
      ],
    );
  }
}
