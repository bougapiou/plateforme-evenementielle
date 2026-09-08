import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/format.dart';
import '../../core/image_field.dart';
import '../../core/media.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class ProgrammeEditorScreen extends ConsumerStatefulWidget {
  final String eventId;
  const ProgrammeEditorScreen({super.key, required this.eventId});

  @override
  ConsumerState<ProgrammeEditorScreen> createState() =>
      _ProgrammeEditorScreenState();
}

class _ProgrammeEditorScreenState extends ConsumerState<ProgrammeEditorScreen> {
  late Future<List<Activity>> _future;

  @override
  void initState() {
    super.initState();
    _future =
        ref.read(organizerEventsRepositoryProvider).activities(widget.eventId);
  }

  void _refresh() => setState(() => _future =
      ref.read(organizerEventsRepositoryProvider).activities(widget.eventId));

  Future<void> _edit([Activity? a]) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _ActivitySheet(eventId: widget.eventId, activity: a),
    );
    if (saved == true) _refresh();
  }

  Future<void> _delete(Activity a) async {
    try {
      await ref
          .read(organizerEventsRepositoryProvider)
          .deleteActivity(widget.eventId, a.id);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Programme')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _edit(),
        icon: const Icon(Icons.add),
        label: const Text('Activité'),
      ),
      body: FutureView<List<Activity>>(
        future: _future,
        onRetry: _refresh,
        builder: (list) {
          if (list.isEmpty) {
            return const EmptyState(
                icon: Icons.event_note_outlined, title: 'Aucune activité');
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            itemBuilder: (_, i) {
              final a = list[i];
              return Card(
                clipBehavior: Clip.antiAlias,
                margin: const EdgeInsets.only(bottom: 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (a.imageUrl != null)
                      AspectRatio(
                          aspectRatio: 16 / 6,
                          child: RemoteImage(url: a.imageUrl)),
                    ListTile(
                      title: Text(a.titre),
                      subtitle: Text([
                        Fmt.dateTime(a.dateDebut),
                        if (a.salle != null) a.salle!,
                        if (a.typeActivite != null) a.typeActivite!,
                      ].join(' · ')),
                      trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                        IconButton(
                            icon: const Icon(Icons.edit_outlined),
                            onPressed: () => _edit(a)),
                        IconButton(
                            icon: const Icon(Icons.delete_outline),
                            onPressed: () => _delete(a)),
                      ]),
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}

class _ActivitySheet extends ConsumerStatefulWidget {
  final String eventId;
  final Activity? activity;
  const _ActivitySheet({required this.eventId, this.activity});

  @override
  ConsumerState<_ActivitySheet> createState() => _ActivitySheetState();
}

class _ActivitySheetState extends ConsumerState<_ActivitySheet> {
  final _titre = TextEditingController();
  final _description = TextEditingController();
  final _salle = TextEditingController();
  final _intervenant = TextEditingController();
  final _moderateur = TextEditingController();
  final _capacite = TextEditingController();
  String? _type;
  DateTime? _debut;
  DateTime? _fin;
  String? _imageUrl;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final a = widget.activity;
    if (a != null) {
      _titre.text = a.titre;
      _description.text = a.description ?? '';
      _salle.text = a.salle ?? '';
      _intervenant.text = a.intervenant ?? '';
      _moderateur.text = a.moderateur ?? '';
      _capacite.text = a.capacite?.toString() ?? '';
      _type = a.typeActivite;
      _debut = a.dateDebut?.toLocal();
      _fin = a.dateFin?.toLocal();
      _imageUrl = a.imageUrl;
    }
  }

  @override
  void dispose() {
    for (final c in [_titre, _description, _salle, _intervenant, _moderateur, _capacite]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<DateTime?> _pick(DateTime? initial) async {
    final now = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: initial ?? now,
      firstDate: DateTime(now.year - 2),
      lastDate: DateTime(now.year + 5),
    );
    if (d == null || !mounted) return null;
    final t = await showTimePicker(
        context: context, initialTime: TimeOfDay.fromDateTime(initial ?? now));
    if (t == null) return null;
    return DateTime(d.year, d.month, d.day, t.hour, t.minute);
  }

  Future<void> _save() async {
    if (_titre.text.trim().isEmpty || _debut == null) {
      showSnack(context, 'Titre et date de début requis.', error: true);
      return;
    }
    setState(() => _saving = true);
    final body = {
      'titre': _titre.text.trim(),
      if (_description.text.trim().isNotEmpty) 'description': _description.text.trim(),
      if (_type != null) 'typeActivite': _type,
      'dateDebut': _debut!.toUtc().toIso8601String(),
      if (_fin != null) 'dateFin': _fin!.toUtc().toIso8601String(),
      if (_salle.text.trim().isNotEmpty) 'salle': _salle.text.trim(),
      if (_intervenant.text.trim().isNotEmpty) 'intervenant': _intervenant.text.trim(),
      if (_moderateur.text.trim().isNotEmpty) 'moderateur': _moderateur.text.trim(),
      if (_capacite.text.trim().isNotEmpty) 'capacite': int.tryParse(_capacite.text.trim()),
      if (_imageUrl != null) 'imageUrl': _imageUrl,
    };
    try {
      await ref.read(organizerEventsRepositoryProvider).saveActivity(
            widget.eventId,
            body,
            id: widget.activity?.id,
          );
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
            Text(widget.activity == null ? 'Nouvelle activité' : 'Modifier l\'activité',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            TextField(controller: _titre, decoration: const InputDecoration(labelText: 'Titre *')),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              value: _type,
              decoration: const InputDecoration(labelText: 'Type'),
              items: activityTypes
                  .map((t) => DropdownMenuItem(value: t, child: Text(t)))
                  .toList(),
              onChanged: (v) => setState(() => _type = v),
            ),
            const SizedBox(height: 10),
            _dateRow('Début *', _debut, (d) => setState(() => _debut = d)),
            _dateRow('Fin', _fin, (d) => setState(() => _fin = d)),
            const SizedBox(height: 10),
            TextField(controller: _salle, decoration: const InputDecoration(labelText: 'Salle')),
            const SizedBox(height: 10),
            TextField(controller: _intervenant, decoration: const InputDecoration(labelText: 'Intervenant')),
            const SizedBox(height: 10),
            TextField(controller: _moderateur, decoration: const InputDecoration(labelText: 'Modérateur')),
            const SizedBox(height: 10),
            TextField(
              controller: _capacite,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Capacité'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _description,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Description'),
            ),
            const SizedBox(height: 12),
            ImageField(
              label: 'Visuel de l\'activité',
              value: _imageUrl,
              folder: 'activites',
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

  Widget _dateRow(String label, DateTime? v, ValueChanged<DateTime?> onSet) => ListTile(
        contentPadding: EdgeInsets.zero,
        dense: true,
        leading: const Icon(Icons.schedule),
        title: Text(label),
        subtitle: Text(v == null ? 'Non défini' : Fmt.dateTime(v)),
        onTap: () async {
          final d = await _pick(v);
          if (d != null) onSet(d);
        },
      );
}
