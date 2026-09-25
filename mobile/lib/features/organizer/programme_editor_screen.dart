import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/image_field.dart';
import '../../core/manage_kit.dart';
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
  static final _dayFmt = DateFormat('EEEE d MMMM', 'fr');

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
    final saved = await showEditorSheet<bool>(
      context,
      (_) => _ActivitySheet(eventId: widget.eventId, activity: a),
    );
    if (saved == true) _refresh();
  }

  Future<void> _delete(Activity a) async {
    final ok = await confirmAction(
      context,
      title: 'Supprimer « ${a.titre} » ?',
      message: 'Cette activité sera retirée du programme.',
      confirmLabel: 'Supprimer',
      destructive: true,
    );
    if (!ok) return;
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
            return EmptyState(
              icon: Icons.event_note_outlined,
              title: 'Aucune activité',
              subtitle:
                  'Ajoutez les conférences, ateliers et spectacles du programme, avec leur horaire.',
              action: FilledButton.icon(
                onPressed: () => _edit(),
                icon: const Icon(Icons.add),
                label: const Text('Ajouter une activité'),
              ),
            );
          }
          final sorted = [...list]..sort((a, b) => (a.dateDebut ?? DateTime(0))
              .compareTo(b.dateDebut ?? DateTime(0)));
          final children = <Widget>[];
          String? lastDay;
          for (final a in sorted) {
            final day = a.dateDebut == null ? 'Sans date' : _dayFmt.format(a.dateDebut!.toLocal());
            if (day != lastDay) {
              children.add(Padding(
                padding: EdgeInsets.fromLTRB(4, lastDay == null ? 0 : 12, 4, 8),
                child: Text(day.toUpperCase(),
                    style: Theme.of(context)
                        .textTheme
                        .labelSmall
                        ?.copyWith(letterSpacing: .8, color: Brand.s500)),
              ));
              lastDay = day;
            }
            children.add(_card(a));
          }
          return MaxWidth(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
              children: children,
            ),
          );
        },
      ),
    );
  }

  Widget _card(Activity a) {
    final (accesLabel, accesTone) = switch (a.acces) {
      'GRATUIT' => ('Gratuit sur billet', KitTone.green),
      'PAYANT' => ('Payant', KitTone.amber),
      _ => ("Billet de l'événement", KitTone.slate),
    };
    return ItemCard(
      leading: _TimeBlock(start: a.dateDebut, end: a.dateFin),
      title: a.titre,
      subtitle: a.intervenant,
      onTap: () => _edit(a),
      chips: [
        MiniChip(accesLabel, tone: accesTone),
        if (a.typeActivite != null)
          MiniChip(activityTypeLabel(a.typeActivite), tone: KitTone.blue),
        if (a.salle != null && a.salle!.isNotEmpty)
          MiniChip(a.salle!, icon: Icons.meeting_room_outlined),
      ],
      footer: a.imageUrl == null
          ? null
          : ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: AspectRatio(
                aspectRatio: 16 / 6,
                child: RemoteImage(url: a.imageUrl),
              ),
            ),
      actions: [
        ItemAction('Modifier', Icons.edit_outlined, () => _edit(a)),
        ItemAction('Supprimer', Icons.delete_outline, () => _delete(a),
            destructive: true),
      ],
    );
  }
}

/// Start time (and end, when known) of an activity, in a tinted square.
class _TimeBlock extends StatelessWidget {
  final DateTime? start;
  final DateTime? end;
  const _TimeBlock({required this.start, required this.end});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 56,
      padding: const EdgeInsets.symmetric(vertical: 8),
      decoration: BoxDecoration(
        color: KitTone.blue.bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(start == null ? '—' : Fmt.time(start),
            style: TextStyle(
                fontSize: 15, fontWeight: FontWeight.w800, color: KitTone.blue.fg)),
        if (end != null)
          Text(Fmt.time(end),
              style: TextStyle(fontSize: 12, color: KitTone.blue.fg.withValues(alpha: .7))),
      ]),
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
  String _acces = 'SANS_BILLET';
  DateTime? _debut;
  DateTime? _fin;
  String? _imageUrl;
  bool _saving = false;
  bool _submitted = false;

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
      _acces = a.acces;
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
    setState(() => _submitted = true);
    if (_titre.text.trim().isEmpty || _debut == null) {
      showSnack(context, 'Titre et date de début requis.', error: true);
      return;
    }
    setState(() => _saving = true);
    final body = {
      'titre': _titre.text.trim(),
      if (_description.text.trim().isNotEmpty) 'description': _description.text.trim(),
      if (_type != null) 'typeActivite': _type,
      'acces': _acces,
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
    return SheetScaffold(
      title: widget.activity == null ? 'Nouvelle activité' : "Modifier l'activité",
      subtitle: 'Créneau du programme',
      icon: Icons.event_note_outlined,
      tone: KitTone.blue,
      action: FilledButton(
        onPressed: _saving ? null : _save,
        child: Text(_saving ? 'Enregistrement…' : 'Enregistrer'),
      ),
      children: [
        TextField(
          controller: _titre,
          textCapitalization: TextCapitalization.sentences,
          decoration: InputDecoration(
            labelText: 'Titre *',
            errorText: _submitted && _titre.text.trim().isEmpty ? 'Requis' : null,
          ),
        ),
        DropdownButtonFormField<String>(
          value: _type,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Type'),
          items: activityTypes
              .map((t) => DropdownMenuItem(value: t, child: Text(activityTypeLabel(t))))
              .toList(),
          onChanged: (v) => setState(() => _type = v),
        ),
        DropdownButtonFormField<String>(
          value: _acces,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Accès'),
          items: const [
            DropdownMenuItem(
                value: 'SANS_BILLET',
                child: Text("Sans billet (billet de l'événement)")),
            DropdownMenuItem(value: 'GRATUIT', child: Text('Gratuit sur billet')),
            DropdownMenuItem(value: 'PAYANT', child: Text('Payant (billets dédiés)')),
          ],
          onChanged: (v) => setState(() => _acces = v ?? 'SANS_BILLET'),
        ),
        DateField(
          label: 'Début *',
          value: _debut,
          format: Fmt.dateTime,
          errorText: _submitted && _debut == null ? 'Requis' : null,
          onTap: () async {
            final d = await _pick(_debut);
            if (d != null) setState(() => _debut = d);
          },
        ),
        DateField(
          label: 'Fin',
          value: _fin,
          format: Fmt.dateTime,
          onClear: () => setState(() => _fin = null),
          onTap: () async {
            final d = await _pick(_fin ?? _debut);
            if (d != null) setState(() => _fin = d);
          },
        ),
        TextField(
          controller: _salle,
          decoration: const InputDecoration(labelText: 'Salle'),
        ),
        TextField(
          controller: _intervenant,
          decoration: const InputDecoration(labelText: 'Intervenant'),
        ),
        TextField(
          controller: _moderateur,
          decoration: const InputDecoration(labelText: 'Modérateur'),
        ),
        TextField(
          controller: _capacite,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'Capacité'),
        ),
        TextField(
          controller: _description,
          maxLines: 3,
          decoration: const InputDecoration(labelText: 'Description'),
        ),
        ImageField(
          label: "Visuel de l'activité",
          value: _imageUrl,
          folder: 'activites',
          onChanged: (v) => setState(() => _imageUrl = v),
        ),
      ],
    );
  }
}
