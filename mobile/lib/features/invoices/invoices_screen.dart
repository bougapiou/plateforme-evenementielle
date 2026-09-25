import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/documents.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class InvoicesScreen extends ConsumerStatefulWidget {
  const InvoicesScreen({super.key});

  @override
  ConsumerState<InvoicesScreen> createState() => _InvoicesScreenState();
}

class _InvoicesScreenState extends ConsumerState<InvoicesScreen> {
  late Future<List<Invoice>> _future;
  String? _busyId;

  @override
  void initState() {
    super.initState();
    _future = ref.read(invoicesRepositoryProvider).mine();
  }

  void _refresh() =>
      setState(() => _future = ref.read(invoicesRepositoryProvider).mine());

  Future<void> _open(Invoice inv) async {
    setState(() => _busyId = inv.id);
    try {
      final outcome = await fetchAndPresentDocument(
        ref.read(apiClientProvider),
        path: inv.pdfUrl,
        filename: '${inv.type.toLowerCase()}-${inv.numero}.pdf',
      );
      if (mounted && outcome == DocOutcome.savedNoViewer) {
        showSnack(context, 'PDF enregistré ; aucune application pour l\'ouvrir.',
            error: true);
      } else if (mounted && outcome == DocOutcome.downloaded) {
        showSnack(context, 'Téléchargement du PDF lancé.');
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Téléchargement impossible : $e', error: true);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Factures & reçus')),
      body: RefreshIndicator(
        onRefresh: () async => _refresh(),
        child: FutureView<List<Invoice>>(
          future: _future,
          onRetry: _refresh,
          builder: (list) {
            if (list.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 120),
                EmptyState(
                  icon: Icons.receipt_long_outlined,
                  title: 'Aucun document',
                  subtitle:
                      'Vos factures et reçus sont générés après chaque paiement confirmé.',
                ),
              ]);
            }
            return MaxWidth(
              child: ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: list.length,
              itemBuilder: (_, i) {
                final inv = list[i];
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    leading: IconBubble(
                        inv.type == 'FACTURE'
                            ? Icons.description_outlined
                            : Icons.receipt_outlined,
                        tone: inv.type == 'FACTURE' ? KitTone.blue : KitTone.green),
                    title: Text('${inv.type == 'FACTURE' ? 'Facture' : 'Reçu'} ${inv.numero}'),
                    subtitle: Text([
                      inv.montantFormatte ?? Fmt.money(inv.montant, inv.devise ?? 'XOF'),
                      Fmt.date(inv.emiseLe),
                    ].join(' · ')),
                    trailing: _busyId == inv.id
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.download_outlined),
                    onTap: _busyId == null ? () => _open(inv) : null,
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
