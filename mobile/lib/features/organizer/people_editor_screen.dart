import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/image_field.dart';
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
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _PersonSheet(
        eventId: widget.eventId,
        isSpeakers: widget.isSpeakers,
        speaker: person is Speaker ? person : null,
        partner: person is Partner ? person : null,
      ),
    );
    if (saved == true) _refresh();
  }

  Future<void> _delete(dynamic person) async {
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
    final title = widget.isSpeakers ? 'Intervenants' : 'Partenaires';
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _edit(),
        icon: const Icon(Icons.add),
        label: Text(widget.isSpeakers ? 'Intervenant' : 'Partenaire'),
      ),
      body: FutureView<List<dynamic>>(
        future: _future,
        onRetry: _refresh,
        builder: (list) {
          if (list.isEmpty) {
            return EmptyState(
                icon: widget.isSpeakers
                    ? Icons.record_voice_over_outlined
                    : Icons.handshake_outlined,
                title: 'Aucun ${widget.isSpeakers ? 'intervenant' : 'partenaire'}');
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            itemBuilder: (_, i) {
              final p = list[i];
              final name = widget.isSpeakers
                  ? (p as Speaker).nom
                  : (p as Partner).nom;
              final sub = widget.isSpeakers
                  ? [(p as Speaker).titre, p.organisation]
                      .where((x) => x != null && x.isNotEmpty)
                      .join(' · ')
                  : ((p as Partner).niveau ?? '');
              final img = widget.isSpeakers
                  ? (p as Speaker).photoUrl
                  : (p as Partner).logoUrl;
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                child: ListTile(
                  leading: img != null
                      ? SizedBox(
                          width: 44, height: 44, child: RemoteImage(url: img))
                      : CircleAvatar(
                          child: Icon(widget.isSpeakers
                              ? Icons.person
                              : Icons.business)),
                  title: Text(name),
                  subtitle: sub.isEmpty ? null : Text(sub),
                  trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                    IconButton(
                        icon: const Icon(Icons.edit_outlined),
                        onPressed: () => _edit(p)),
                    IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => _delete(p)),
                  ]),
                ),
              );
            },
          );
        },
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
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              _sp ? 'Intervenant' : 'Partenaire',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 12),
            TextField(controller: _nom, decoration: const InputDecoration(labelText: 'Nom *')),
            const SizedBox(height: 10),
            if (_sp) ...[
              TextField(controller: _a, decoration: const InputDecoration(labelText: 'Titre / fonction')),
              const SizedBox(height: 10),
              TextField(controller: _b, decoration: const InputDecoration(labelText: 'Organisation')),
              const SizedBox(height: 10),
              TextField(
                controller: _bio,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'Bio'),
              ),
            ] else ...[
              DropdownButtonFormField<String>(
                value: _niveau,
                decoration: const InputDecoration(labelText: 'Niveau'),
                items: partnerLevels
                    .map((n) => DropdownMenuItem(value: n, child: Text(n)))
                    .toList(),
                onChanged: (v) => setState(() => _niveau = v),
              ),
              const SizedBox(height: 10),
              TextField(controller: _a, decoration: const InputDecoration(labelText: 'Site web')),
            ],
            const SizedBox(height: 12),
            ImageField(
              label: _sp ? 'Photo' : 'Logo',
              value: _imageUrl,
              folder: 'activites',
              height: 90,
              onChanged: (v) => setState(() => _imageUrl = v),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _saving ? null : _save,
              child: Text(_saving ? 'Enregistrement…' : 'Enregistrer'),
            ),
          ],
        ),
      ),
    );
  }
}
