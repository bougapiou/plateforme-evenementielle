import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/manage_kit.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

const structureTypeLabels = <String, String>{
  'ENTREPRISE': 'Entreprise',
  'INSTITUTION': 'Institution',
  'ADMINISTRATION_PUBLIQUE': 'Administration publique',
  'ONG_ASSOCIATION': 'ONG / Association',
  'ETABLISSEMENT_SCOLAIRE': 'Établissement scolaire',
  'AUTRE': 'Autre',
};

class StructuresScreen extends ConsumerStatefulWidget {
  const StructuresScreen({super.key});

  @override
  ConsumerState<StructuresScreen> createState() => _StructuresScreenState();
}

class _StructuresScreenState extends ConsumerState<StructuresScreen> {
  late Future<List<StructureSummary>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(structuresRepositoryProvider).mine();
  }

  void _refresh() {
    setState(() => _future = ref.read(structuresRepositoryProvider).mine());
    ref.invalidate(myStructuresProvider);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mes structures')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await context.push('/structures/nouvelle');
          _refresh();
        },
        icon: const Icon(Icons.add),
        label: const Text('Créer'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => _refresh(),
        child: FutureView<List<StructureSummary>>(
          future: _future,
          onRetry: _refresh,
          builder: (list) {
            if (list.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 100),
                EmptyState(
                  icon: Icons.domain_outlined,
                  title: 'Aucune structure',
                  subtitle:
                      'Créez une structure pour vous inscrire ou réserver des stands en son nom.',
                ),
              ]);
            }
            return MaxWidth(
              child: ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: list.length,
              itemBuilder: (_, i) {
                final s = list[i];
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    leading: const IconBubble(Icons.domain, tone: KitTone.slate),
                    title: Text(s.raisonSociale),
                    subtitle: Text([
                      structureTypeLabels[s.typeStructure] ?? s.typeStructure,
                      if (s.ville != null) s.ville!,
                    ].join(' · ')),
                    trailing: StatusChip(s.statut),
                    onTap: () async {
                      await context.push('/structures/${s.id}');
                      _refresh();
                    },
                  ),
                );
              },
            ),
            );
          },
        ),
      ),
    );
  }
}
