import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/providers.dart';

/// Lets a user choose to act on behalf of one of their structures.
/// Emits `asStructure` (the switch) and the selected `structureId`.
class StructureToggle extends ConsumerWidget {
  final bool asStructure;
  final String? structureId;
  final ValueChanged<bool> onModeChanged;
  final ValueChanged<String?> onStructureChanged;
  final String label;

  const StructureToggle({
    super.key,
    required this.asStructure,
    required this.structureId,
    required this.onModeChanged,
    required this.onStructureChanged,
    this.label = 'Au nom d\'une structure',
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final structures = ref.watch(myStructuresProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(label),
          value: asStructure,
          onChanged: onModeChanged,
        ),
        if (asStructure)
          structures.when(
            loading: () => const LinearProgressIndicator(),
            error: (e, _) => Text('Impossible de charger vos structures : $e',
                style: const TextStyle(color: Color(0xFF991B1B))),
            data: (list) {
              if (list.isEmpty) {
                return Card(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                            'Vous n\'avez aucune structure. Créez-en une pour '
                            'agir en son nom.'),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          onPressed: () => context.push('/structures/nouvelle'),
                          icon: const Icon(Icons.add),
                          label: const Text('Créer une structure'),
                        ),
                      ],
                    ),
                  ),
                );
              }
              return DropdownButtonFormField<String>(
                value: structureId,
                decoration: const InputDecoration(labelText: 'Structure'),
                items: list
                    .map((s) => DropdownMenuItem(
                        value: s.id,
                        child: Text(
                          s.raisonSociale +
                              (s.verifiee ? '' : ' (en attente de vérification)'),
                        )))
                    .toList(),
                onChanged: onStructureChanged,
              );
            },
          ),
      ],
    );
  }
}
