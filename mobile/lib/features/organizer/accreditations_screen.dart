import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/documents.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class AccreditationsScreen extends ConsumerStatefulWidget {
  final String eventId;
  const AccreditationsScreen({super.key, required this.eventId});

  @override
  ConsumerState<AccreditationsScreen> createState() =>
      _AccreditationsScreenState();
}

class _AccreditationsScreenState extends ConsumerState<AccreditationsScreen> {
  late Future<List<Accreditation>> _future;
  String? _busyId;

  @override
  void initState() {
    super.initState();
    _future = _load();
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
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _AccreditationSheet(
        eventId: widget.eventId,
        activities: activities,
      ),
    );
    if (created == true) _refresh();
  }

  Future<void> _revoke(Accreditation a) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Révoquer le badge ?'),
        content: Text('${a.personneNom} — ${a.fonctionLibelle}. '
            'Le QR ne sera plus valable à l\'entrée.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Annuler')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Révoquer')),
        ],
      ),
    );
    if (ok != true) return;
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
        showSnack(context, 'PDF enregistré ; aucune application pour l\'ouvrir.',
            error: true);
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Badge indisponible : $e', error: true);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
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
            return const EmptyState(
              icon: Icons.badge_outlined,
              title: 'Aucune accréditation',
              subtitle:
                  'Délivrez des badges nominatifs (conférencier, exposant, presse, '
                  'staff…) pour une activité ou pour tout l\'événement.',
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            itemBuilder: (_, i) {
              final a = list[i];
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Text(a.personneNom,
                      style: TextStyle(
                        decoration: a.active ? null : TextDecoration.lineThrough,
                      )),
                  subtitle: Text([
                    a.fonctionLibelle,
                    if (a.organisation != null) a.organisation!,
                    a.activiteNom ?? 'Événement entier',
                    if (!a.active) 'RÉVOQUÉ',
                  ].join(' · ')),
                  trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                    if (_busyId == a.id)
                      const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2))
                    else
                      IconButton(
                        icon: const Icon(Icons.picture_as_pdf_outlined),
                        tooltip: 'Badge PDF',
                        onPressed: () => _badge(a),
                      ),
                    if (a.active)
                      IconButton(
                        icon: const Icon(Icons.block, color: Color(0xFF991B1B)),
                        tooltip: 'Révoquer',
                        onPressed: () => _revoke(a),
                      ),
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
          if (_fonction == 'AUTRE')
            'fonctionLibre': _fonctionLibre.text.trim(),
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
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Nouveau badge',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            TextField(
                controller: _nom,
                decoration:
                    const InputDecoration(labelText: 'Nom de la personne *')),
            const SizedBox(height: 10),
            TextField(
                controller: _organisation,
                decoration: const InputDecoration(labelText: 'Organisation')),
            const SizedBox(height: 10),
            TextField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration:
                    const InputDecoration(labelText: 'E-mail (optionnel)')),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              value: _fonction,
              decoration: const InputDecoration(labelText: 'Fonction'),
              items: kAccreditationRoles.entries
                  .map((e) =>
                      DropdownMenuItem(value: e.key, child: Text(e.value)))
                  .toList(),
              onChanged: (v) => setState(() => _fonction = v ?? 'CONFERENCIER'),
            ),
            if (_fonction == 'AUTRE') ...[
              const SizedBox(height: 10),
              TextField(
                  controller: _fonctionLibre,
                  decoration: const InputDecoration(
                      labelText: 'Fonction (texte libre) *')),
            ],
            const SizedBox(height: 10),
            DropdownButtonFormField<String?>(
              value: _activityId,
              decoration: const InputDecoration(labelText: 'Portée'),
              items: [
                const DropdownMenuItem(
                    value: null, child: Text('Toutes les activités')),
                ...widget.activities.map((a) =>
                    DropdownMenuItem(value: a.id, child: Text(a.titre))),
              ],
              onChanged: (v) => setState(() => _activityId = v),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _saving ? null : _save,
              child: Text(_saving ? 'Création…' : 'Délivrer le badge'),
            ),
          ],
        ),
      ),
    );
  }
}
