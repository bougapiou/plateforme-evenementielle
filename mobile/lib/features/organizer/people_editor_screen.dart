import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/brand.dart';
import '../../core/image_field.dart';
import '../../core/manage_kit.dart';
import '../../core/media.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import '../../data/organizer_events_repository.dart';

/// Editor for speakers (`kind == 'speakers'`) or partners (`kind == 'partners'`).
class PeopleEditorScreen extends ConsumerStatefulWidget {
  final String eventId;
  final String kind;
  const PeopleEditorScreen({super.key, required this.eventId, required this.kind});

  bool get isSpeakers => kind == 'speakers';

  @override
  ConsumerState<PeopleEditorScreen> createState() => _PeopleEditorScreenState();
}

class _PeopleEditorScreenState extends ConsumerState<PeopleEditorScreen> {
  late Future<List<dynamic>> _future;

  OrganizerEventsRepository get _repo =>
      ref.read(organizerEventsRepositoryProvider);

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<List<dynamic>> _load() => widget.isSpeakers
      ? _repo.speakers(widget.eventId)
      : _repo.partners(widget.eventId);

  void _refresh() => setState(() => _future = _load());

  Future<void> _edit([dynamic person]) async {
    final saved = await showEditorSheet<bool>(
      context,
      (_) => _PersonSheet(
        eventId: widget.eventId,
        isSpeakers: widget.isSpeakers,
        speaker: person is Speaker ? person : null,
        partner: person is Partner ? person : null,
      ),
    );
    if (saved == true) _refresh();
  }

  Future<void> _delete(dynamic person) async {
    final name = widget.isSpeakers ? (person as Speaker).nom : (person as Partner).nom;
    final ok = await confirmAction(
      context,
      title: 'Retirer « $name » ?',
      confirmLabel: 'Retirer',
      destructive: true,
    );
    if (!ok) return;
    try {
      if (widget.isSpeakers) {
        await _repo.deleteSpeaker(widget.eventId, (person as Speaker).id);
      } else {
        await _repo.deletePartner(widget.eventId, (person as Partner).id);
      }
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final sp = widget.isSpeakers;
    return Scaffold(
      appBar: AppBar(title: Text(sp ? 'Intervenants' : 'Partenaires')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _edit(),
        icon: const Icon(Icons.add),
        label: Text(sp ? 'Intervenant' : 'Partenaire'),
      ),
      body: FutureView<List<dynamic>>(
        future: _future,
        onRetry: _refresh,
        builder: (list) {
          if (list.isEmpty) {
            return EmptyState(
              icon: sp ? Icons.record_voice_over_outlined : Icons.handshake_outlined,
              title: 'Aucun ${sp ? 'intervenant' : 'partenaire'}',
              subtitle: sp
                  ? 'Présentez les conférenciers et invités : ils apparaissent sur la page publique.'
                  : 'Présentez les sponsors et institutions partenaires, avec leur logo.',
              action: FilledButton.icon(
                onPressed: () => _edit(),
                icon: const Icon(Icons.add),
                label: Text(sp ? 'Ajouter un intervenant' : 'Ajouter un partenaire'),
              ),
            );
          }
          return MaxWidth(
            child: ListView.builder(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
              itemCount: list.length,
              itemBuilder: (_, i) => sp ? _speaker(list[i] as Speaker) : _partner(list[i] as Partner),
            ),
          );
        },
      ),
    );
  }

  Widget _speaker(Speaker s) {
    final sub = [s.titre, s.organisation]
        .where((x) => x != null && x.isNotEmpty)
        .join(' · ');
    return ItemCard(
      leading: _Avatar(url: s.photoUrl, name: s.nom, round: true),
      title: s.nom,
      subtitle: sub,
      onTap: () => _edit(s),
      actions: [
        ItemAction('Modifier', Icons.edit_outlined, () => _edit(s)),
        ItemAction('Retirer', Icons.delete_outline, () => _delete(s), destructive: true),
      ],
    );
  }

  Widget _partner(Partner p) {
    return ItemCard(
      leading: _Avatar(url: p.logoUrl, name: p.nom, round: false),
      title: p.nom,
      subtitle: p.siteWeb,
      onTap: () => _edit(p),
      chips: [
        if (p.niveau != null && p.niveau!.isNotEmpty)
          MiniChip(partnerLevelLabel(p.niveau), tone: KitTone.amber),
      ],
      actions: [
        ItemAction('Modifier', Icons.edit_outlined, () => _edit(p)),
        ItemAction('Retirer', Icons.delete_outline, () => _delete(p), destructive: true),
      ],
    );
  }
}

/// Photo or logo in a 48 px frame; the first letter of the name when there is no image.
class _Avatar extends StatelessWidget {
  final String? url;
  final String name;
  final bool round;
  const _Avatar({required this.url, required this.name, required this.round});

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(round ? 24 : 12);
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: Brand.s100,
        borderRadius: radius,
        border: Border.all(color: Brand.s200),
      ),
      clipBehavior: Clip.antiAlias,
      alignment: Alignment.center,
      child: url == null
          ? Text(
              name.isEmpty ? '?' : name.substring(0, 1).toUpperCase(),
              style: const TextStyle(
                  fontSize: 18, fontWeight: FontWeight.w700, color: Brand.s500),
            )
          : RemoteImage(
              url: url,
              width: 48,
              height: 48,
              // a logo must stay whole; a face fills its frame
              fit: round ? BoxFit.cover : BoxFit.contain,
            ),
    );
  }
}

class _PersonSheet extends ConsumerStatefulWidget {
  final String eventId;
  final bool isSpeakers;
  final Speaker? speaker;
  final Partner? partner;
  const _PersonSheet({
    required this.eventId,
    required this.isSpeakers,
    this.speaker,
    this.partner,
  });

  @override
  ConsumerState<_PersonSheet> createState() => _PersonSheetState();
}

class _PersonSheetState extends ConsumerState<_PersonSheet> {
  final _nom = TextEditingController();
  final _a = TextEditingController(); // titre (speaker) / siteWeb (partner)
  final _b = TextEditingController(); // organisation (speaker) / — (partner)
  final _bio = TextEditingController();
  String? _niveau;
  String? _imageUrl;
  bool _saving = false;

  bool get _sp => widget.isSpeakers;

  @override
  void initState() {
    super.initState();
    if (widget.speaker != null) {
      final s = widget.speaker!;
      _nom.text = s.nom;
      _a.text = s.titre ?? '';
      _b.text = s.organisation ?? '';
      _bio.text = s.bio ?? '';
      _imageUrl = s.photoUrl;
    } else if (widget.partner != null) {
      final p = widget.partner!;
      _nom.text = p.nom;
      _a.text = p.siteWeb ?? '';
      _niveau = p.niveau;
      _imageUrl = p.logoUrl;
    }
  }

  @override
  void dispose() {
    for (final c in [_nom, _a, _b, _bio]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (_nom.text.trim().isEmpty) {
      showSnack(context, 'Nom requis.', error: true);
      return;
    }
    setState(() => _saving = true);
    final repo = ref.read(organizerEventsRepositoryProvider);
    try {
      if (_sp) {
        await repo.saveSpeaker(widget.eventId, {
          'nom': _nom.text.trim(),
          if (_a.text.trim().isNotEmpty) 'titre': _a.text.trim(),
          if (_b.text.trim().isNotEmpty) 'organisation': _b.text.trim(),
          if (_bio.text.trim().isNotEmpty) 'bio': _bio.text.trim(),
          if (_imageUrl != null) 'photoUrl': _imageUrl,
        }, id: widget.speaker?.id);
      } else {
        await repo.savePartner(widget.eventId, {
          'nom': _nom.text.trim(),
          if (_niveau != null) 'niveau': _niveau,
          if (_a.text.trim().isNotEmpty) 'siteWeb': _a.text.trim(),
          if (_imageUrl != null) 'logoUrl': _imageUrl,
        }, id: widget.partner?.id);
      }
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final editing = widget.speaker != null || widget.partner != null;
    return SheetScaffold(
      title: _sp
          ? (editing ? "Modifier l'intervenant" : 'Nouvel intervenant')
          : (editing ? 'Modifier le partenaire' : 'Nouveau partenaire'),
      subtitle: _sp ? 'Conférencier ou invité' : 'Sponsor ou institution',
      icon: _sp ? Icons.record_voice_over_outlined : Icons.handshake_outlined,
      tone: KitTone.slate,
      action: FilledButton(
        onPressed: _saving ? null : _save,
        child: Text(_saving ? 'Enregistrement…' : 'Enregistrer'),
      ),
      children: [
        TextField(
          controller: _nom,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(labelText: 'Nom *'),
        ),
        if (_sp) ...[
          TextField(
            controller: _a,
            decoration: const InputDecoration(labelText: 'Titre / fonction'),
          ),
          TextField(
            controller: _b,
            decoration: const InputDecoration(labelText: 'Organisation'),
          ),
          TextField(
            controller: _bio,
            maxLines: 3,
            decoration: const InputDecoration(labelText: 'Bio'),
          ),
        ] else ...[
          DropdownButtonFormField<String>(
            value: _niveau,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Niveau'),
            items: partnerLevels
                .map((n) => DropdownMenuItem(value: n, child: Text(partnerLevelLabel(n))))
                .toList(),
            onChanged: (v) => setState(() => _niveau = v),
          ),
          TextField(
            controller: _a,
            keyboardType: TextInputType.url,
            decoration: const InputDecoration(labelText: 'Site web'),
          ),
        ],
        ImageField(
          label: _sp ? 'Photo' : 'Logo',
          value: _imageUrl,
          folder: 'activites',
          height: 90,
          onChanged: (v) => setState(() => _imageUrl = v),
        ),
      ],
    );
  }
}
