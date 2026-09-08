import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import 'structures_screen.dart';

class _Data {
  final Structure structure;
  final List<StructureMember> members;
  _Data(this.structure, this.members);
}

class StructureDetailScreen extends ConsumerStatefulWidget {
  final String structureId;
  const StructureDetailScreen({super.key, required this.structureId});

  @override
  ConsumerState<StructureDetailScreen> createState() =>
      _StructureDetailScreenState();
}

class _StructureDetailScreenState extends ConsumerState<StructureDetailScreen> {
  late Future<_Data> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_Data> _load() async {
    final repo = ref.read(structuresRepositoryProvider);
    final s = await repo.get(widget.structureId);
    final m = await repo.members(widget.structureId).catchError((_) => <StructureMember>[]);
    return _Data(s, m);
  }

  void _refresh() => setState(() => _future = _load());

  Future<void> _addMember(Structure s) async {
    final emailCtrl = TextEditingController();
    String role = 'MEMBRE';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: const Text('Ajouter un représentant'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: emailCtrl,
                decoration: const InputDecoration(
                    labelText: 'E-mail du compte à ajouter'),
                keyboardType: TextInputType.emailAddress,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: role,
                decoration: const InputDecoration(labelText: 'Rôle'),
                items: const [
                  DropdownMenuItem(value: 'MEMBRE', child: Text('Membre')),
                  DropdownMenuItem(
                      value: 'ADMINISTRATEUR', child: Text('Administrateur')),
                ],
                onChanged: (v) => setLocal(() => role = v ?? 'MEMBRE'),
              ),
            ],
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Annuler')),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('Ajouter')),
          ],
        ),
      ),
    );
    if (ok != true || emailCtrl.text.trim().isEmpty) return;
    try {
      await ref.read(structuresRepositoryProvider).addMember(
            s.id,
            email: emailCtrl.text.trim(),
            roleInterne: role,
          );
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  Future<void> _removeMember(Structure s, StructureMember m) async {
    if (!await _confirm('Retirer ${m.fullName} de la structure ?')) return;
    try {
      await ref.read(structuresRepositoryProvider).removeMember(s.id, m.userId);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  Future<bool> _confirm(String msg) async =>
      await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          content: Text(msg),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Non')),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('Oui')),
          ],
        ),
      ) ??
      false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Structure')),
      body: FutureView<_Data>(
        future: _future,
        onRetry: _refresh,
        builder: (d) {
          final s = d.structure;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Row(children: [
                Expanded(
                  child: Text(s.raisonSociale,
                      style: Theme.of(context).textTheme.titleLarge),
                ),
                StatusChip(s.statut),
              ]),
              const SizedBox(height: 4),
              Text(structureTypeLabels[s.typeStructure] ?? s.typeStructure,
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 16),
              if (s.sigle != null) _row('Sigle', s.sigle!),
              if (s.secteurActivite != null) _row('Secteur', s.secteurActivite!),
              if (s.rccm != null) _row('RCCM', s.rccm!),
              if (s.ifu != null) _row('IFU', s.ifu!),
              if (s.adresse != null) _row('Adresse', s.adresse!),
              if (s.ville != null) _row('Ville', s.ville!),
              if (s.telephone != null) _row('Téléphone', s.telephone!),
              if (s.email != null) _row('E-mail', s.email!),
              if (s.siteWeb != null) _row('Site web', s.siteWeb!),
              if (s.description != null) ...[
                const SizedBox(height: 8),
                Text(s.description!,
                    style: Theme.of(context).textTheme.bodyMedium),
              ],
              if (s.canManage) ...[
                const SizedBox(height: 16),
                OutlinedButton.icon(
                  onPressed: () async {
                    await context.push('/structures/${s.id}/modifier');
                    _refresh();
                  },
                  icon: const Icon(Icons.edit_outlined),
                  label: const Text('Modifier la structure'),
                ),
              ],
              const Divider(height: 32),
              Row(
                children: [
                  Expanded(
                    child: Text('Représentants (${d.members.length})',
                        style: Theme.of(context).textTheme.titleMedium),
                  ),
                  if (s.canManage)
                    TextButton.icon(
                      onPressed: () => _addMember(s),
                      icon: const Icon(Icons.person_add_alt),
                      label: const Text('Ajouter'),
                    ),
                ],
              ),
              ...d.members.map((m) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const CircleAvatar(child: Icon(Icons.person)),
                    title: Text(m.fullName),
                    subtitle: Text([
                      m.email,
                      _roleLabel(m.roleInterne),
                      if (m.fonction != null) m.fonction!,
                    ].join(' · ')),
                    trailing: (s.canManage && m.roleInterne != 'PROPRIETAIRE')
                        ? IconButton(
                            icon: const Icon(Icons.remove_circle_outline),
                            onPressed: () => _removeMember(s, m),
                          )
                        : null,
                  )),
            ],
          );
        },
      ),
    );
  }

  String _roleLabel(String r) => switch (r) {
        'PROPRIETAIRE' => 'Propriétaire',
        'ADMINISTRATEUR' => 'Administrateur',
        _ => 'Membre',
      };

  Widget _row(String label, String value) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(
              width: 90,
              child: Text(label,
                  style: const TextStyle(color: Color(0xFF64748B)))),
          Expanded(child: Text(value)),
        ]),
      );
}
