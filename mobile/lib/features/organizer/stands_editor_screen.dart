import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class StandsEditorScreen extends ConsumerStatefulWidget {
  final String eventId;
  const StandsEditorScreen({super.key, required this.eventId});

  @override
  ConsumerState<StandsEditorScreen> createState() => _StandsEditorScreenState();
}

class _StandsEditorScreenState extends ConsumerState<StandsEditorScreen> {
  late Future<List<StandType>> _future;

  @override
  void initState() {
    super.initState();
    _future =
        ref.read(organizerEventsRepositoryProvider).standTypes(widget.eventId);
  }

  void _refresh() => setState(() => _future =
      ref.read(organizerEventsRepositoryProvider).standTypes(widget.eventId));

  Future<void> _edit([StandType? t]) async {
    final saved = await showEditorSheet<bool>(
      context,
      (_) => _StandTypeSheet(eventId: widget.eventId, type: t),
    );
    if (saved == true) _refresh();
  }

  Future<void> _delete(StandType t) async {
    final ok = await confirmAction(
      context,
      title: 'Supprimer « ${t.nom} » ?',
      message: 'Ce type de stand et ses emplacements non réservés seront supprimés.',
      confirmLabel: 'Supprimer',
      destructive: true,
    );
    if (!ok) return;
    try {
      await ref
          .read(organizerEventsRepositoryProvider)
          .deleteStandType(widget.eventId, t.id);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Stands')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _edit(),
        icon: const Icon(Icons.add),
        label: const Text('Type de stand'),
      ),
      body: FutureView<List<StandType>>(
        future: _future,
        onRetry: _refresh,
        builder: (list) {
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.storefront_outlined,
              title: 'Aucun type de stand',
              subtitle:
                  'Décrivez les emplacements que les exposants pourront réserver (taille, prix, quantité).',
              action: FilledButton.icon(
                onPressed: () => _edit(),
                icon: const Icon(Icons.add),
                label: const Text('Créer un type de stand'),
              ),
            );
          }
          return MaxWidth(
            child: ListView.builder(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
              itemCount: list.length,
              itemBuilder: (_, i) {
                final t = list[i];
                final taken =
                    (t.quantiteTotale - t.quantiteRestante).clamp(0, t.quantiteTotale);
                final gratuit = t.prixMontant <= 0;
                return ItemCard(
                  icon: Icons.storefront_outlined,
                  tone: KitTone.violet,
                  title: t.nom,
                  subtitle: t.description,
                  onTap: () => _edit(t),
                  chips: [
                    MiniChip(
                      gratuit ? 'Gratuit' : Fmt.money(t.prixMontant, t.devise),
                      tone: gratuit ? KitTone.green : KitTone.amber,
                    ),
                    if (t.dimensions != null && t.dimensions!.isNotEmpty)
                      MiniChip(t.dimensions!, icon: Icons.straighten),
                    MiniChip('${t.quantiteTotale} stands'),
                  ],
                  footer: t.quantiteTotale > 0
                      ? QuotaBar(used: taken, total: t.quantiteTotale, label: 'réservés')
                      : null,
                  actions: [
                    ItemAction('Modifier', Icons.edit_outlined, () => _edit(t)),
                    ItemAction('Supprimer', Icons.delete_outline, () => _delete(t),
                        destructive: true),
                  ],
                );
              },
            ),
          );
        },
      ),
    );
  }
}

class _StandTypeSheet extends ConsumerStatefulWidget {
  final String eventId;
  final StandType? type;
  const _StandTypeSheet({required this.eventId, this.type});

  @override
  ConsumerState<_StandTypeSheet> createState() => _StandTypeSheetState();
}

class _StandTypeSheetState extends ConsumerState<_StandTypeSheet> {
  final _nom = TextEditingController();
  final _dimensions = TextEditingController();
  final _prix = TextEditingController(text: '0');
  final _quantite = TextEditingController(text: '20');
  final _equipements = TextEditingController();
  final _description = TextEditingController();
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final t = widget.type;
    if (t != null) {
      _nom.text = t.nom;
      _dimensions.text = t.dimensions ?? '';
      _prix.text = t.prixMontant.toString();
      _quantite.text = t.quantiteTotale.toString();
      _equipements.text = t.equipements ?? '';
      _description.text = t.description ?? '';
    }
  }

  @override
  void dispose() {
    for (final c in [_nom, _dimensions, _prix, _quantite, _equipements, _description]) {
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
    final body = {
      'nom': _nom.text.trim(),
      if (_dimensions.text.trim().isNotEmpty) 'dimensions': _dimensions.text.trim(),
      'prixMontant': num.tryParse(_prix.text.trim()) ?? 0,
      'devise': 'XOF',
      'quantiteTotale': int.tryParse(_quantite.text.trim()) ?? 1,
      if (_equipements.text.trim().isNotEmpty) 'equipements': _equipements.text.trim(),
      if (_description.text.trim().isNotEmpty) 'description': _description.text.trim(),
    };
    try {
      await ref.read(organizerEventsRepositoryProvider).saveStandType(
            widget.eventId,
            body,
            id: widget.type?.id,
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
      title: widget.type == null ? 'Nouveau type de stand' : 'Modifier le type',
      subtitle: 'Emplacement réservable par les exposants',
      icon: Icons.storefront_outlined,
      tone: KitTone.violet,
      action: FilledButton(
        onPressed: _saving ? null : _save,
        child: Text(_saving ? 'Enregistrement…' : 'Enregistrer'),
      ),
      children: [
        TextField(
          controller: _nom,
          textCapitalization: TextCapitalization.sentences,
          decoration: const InputDecoration(labelText: 'Nom *'),
        ),
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(
            child: TextField(
              controller: _prix,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Prix (FCFA)'),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: TextField(
              controller: _quantite,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Nombre de stands'),
            ),
          ),
        ]),
        TextField(
          controller: _dimensions,
          decoration: const InputDecoration(labelText: 'Dimensions (ex : 3m x 3m)'),
        ),
        TextField(
          controller: _equipements,
          maxLines: 2,
          decoration: const InputDecoration(labelText: 'Équipements inclus'),
        ),
        TextField(
          controller: _description,
          maxLines: 2,
          decoration: const InputDecoration(labelText: 'Description'),
        ),
        const InfoBanner(
          'Modifier le nombre ajuste automatiquement les stands générés '
          '(les stands déjà réservés sont conservés).',
        ),
      ],
    );
  }
}
