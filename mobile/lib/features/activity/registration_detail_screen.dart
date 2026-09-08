import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:open_filex/open_filex.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class _Data {
  final Registration registration;
  final List<DocumentFile> documents;
  _Data(this.registration, this.documents);
}

class RegistrationDetailScreen extends ConsumerStatefulWidget {
  final String registrationId;
  const RegistrationDetailScreen({super.key, required this.registrationId});

  @override
  ConsumerState<RegistrationDetailScreen> createState() =>
      _RegistrationDetailScreenState();
}

class _RegistrationDetailScreenState
    extends ConsumerState<RegistrationDetailScreen> {
  late Future<_Data> _future;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_Data> _load() async {
    final repo = ref.read(registrationsRepositoryProvider);
    final reg = await repo.get(widget.registrationId);
    final docs = await repo
        .documents(widget.registrationId)
        .catchError((_) => <DocumentFile>[]);
    return _Data(reg, docs);
  }

  void _refresh() => setState(() => _future = _load());

  Future<void> _cancel() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        content: const Text('Annuler cette inscription ?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Non')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Oui, annuler')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref.read(registrationsRepositoryProvider).cancel(widget.registrationId);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _downloadConfirmation() async {
    setState(() => _busy = true);
    try {
      final path = await ref.read(apiClientProvider).downloadToCache(
            '/api/registrations/${widget.registrationId}/confirmation.pdf',
            'confirmation-${widget.registrationId}.pdf',
          );
      final res = await OpenFilex.open(path);
      if (res.type != ResultType.done && mounted) {
        showSnack(context, 'PDF enregistré ; aucune application pour l\'ouvrir.',
            error: true);
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Téléchargement impossible : $e', error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _addDocument() async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Prendre une photo'),
              onTap: () => Navigator.pop(ctx, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Choisir dans la galerie'),
              onTap: () => Navigator.pop(ctx, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    final picked = await ImagePicker().pickImage(source: source, imageQuality: 85);
    if (picked == null) return;

    setState(() => _busy = true);
    try {
      await ref.read(registrationsRepositoryProvider).uploadDocument(
            widget.registrationId,
            filePath: picked.path,
            fileName: picked.name,
            type: 'PIECE_JOINTE',
          );
      if (mounted) showSnack(context, 'Document ajouté.');
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _deleteDocument(DocumentFile doc) async {
    setState(() => _busy = true);
    try {
      await ref
          .read(registrationsRepositoryProvider)
          .deleteDocument(widget.registrationId, doc.id);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openDocument(DocumentFile doc) async {
    setState(() => _busy = true);
    try {
      final path = await ref
          .read(apiClientProvider)
          .downloadToCache(doc.url, doc.nom.isEmpty ? 'document' : doc.nom);
      final res = await OpenFilex.open(path);
      if (res.type != ResultType.done && mounted) {
        showSnack(context, 'Aucune application pour ouvrir ce fichier.', error: true);
      }
    } catch (e) {
      if (mounted) showSnack(context, 'Ouverture impossible : $e', error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Inscription')),
      body: FutureView<_Data>(
        future: _future,
        onRetry: _refresh,
        builder: (d) {
          final r = d.registration;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Row(children: [
                Expanded(
                  child: Text(r.eventNom,
                      style: Theme.of(context).textTheme.titleLarge),
                ),
                StatusChip(r.statut),
              ]),
              const SizedBox(height: 4),
              Text('Réf. ${r.reference} · ${r.type == 'STRUCTURE' ? 'Structure' : 'Particulier'}',
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 16),
              if (r.structureNom != null) _row('Structure', r.structureNom!),
              if (r.contactNom != null) _row('Contact', r.contactNom!),
              if (r.contactEmail != null) _row('E-mail', r.contactEmail!),
              if (r.contactTelephone != null)
                _row('Téléphone', r.contactTelephone!),
              _row('Participants', '${r.nombreParticipants}'),
              _row('Créée le', Fmt.dateTime(r.createdAt)),
              if (r.motifRefus != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Card(
                    color: const Color(0xFFFEE2E2),
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Text('Motif du refus : ${r.motifRefus}'),
                    ),
                  ),
                ),

              if (r.participants.isNotEmpty) ...[
                const Divider(height: 28),
                Text('Participants',
                    style: Theme.of(context).textTheme.titleMedium),
                ...r.participants.map((p) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.person_outline),
                      title: Text([p.prenom, p.nom]
                          .where((x) => x != null && x.isNotEmpty)
                          .join(' ')),
                      subtitle: Text([p.email, p.fonction]
                          .whereType<String>()
                          .where((x) => x.isNotEmpty)
                          .join(' · ')),
                    )),
              ],

              const Divider(height: 28),
              Row(children: [
                Expanded(
                  child: Text('Documents (${d.documents.length})',
                      style: Theme.of(context).textTheme.titleMedium),
                ),
                TextButton.icon(
                  onPressed: _busy ? null : _addDocument,
                  icon: const Icon(Icons.attach_file),
                  label: const Text('Ajouter'),
                ),
              ]),
              if (d.documents.isEmpty)
                Text('Aucun document.',
                    style: Theme.of(context).textTheme.bodySmall)
              else
                ...d.documents.map((doc) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.description_outlined),
                      title: Text(doc.nom),
                      subtitle: doc.typeDocument != null
                          ? Text(doc.typeDocument!)
                          : null,
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.download_outlined),
                            onPressed: _busy ? null : () => _openDocument(doc),
                          ),
                          IconButton(
                            icon: const Icon(Icons.delete_outline),
                            onPressed: _busy ? null : () => _deleteDocument(doc),
                          ),
                        ],
                      ),
                    )),

              const SizedBox(height: 24),
              if (r.statut == 'CONFIRMEE')
                OutlinedButton.icon(
                  onPressed: _busy ? null : _downloadConfirmation,
                  icon: const Icon(Icons.picture_as_pdf_outlined),
                  label: const Text('Confirmation (PDF)'),
                ),
              if (r.ticketOrderId != null &&
                  r.ticketOrderStatut == 'EN_ATTENTE') ...[
                const SizedBox(height: 8),
                FilledButton.icon(
                  onPressed: () async {
                    await context
                        .push('/paiement/TICKET_ORDER/${r.ticketOrderId}');
                    _refresh();
                  },
                  icon: const Icon(Icons.payment),
                  label: const Text('Payer les billets'),
                ),
              ],
              if (r.annulable) ...[
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _busy ? null : _cancel,
                  child: const Text('Annuler mon inscription'),
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  Widget _row(String label, String value) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(
              width: 110,
              child: Text(label,
                  style: const TextStyle(color: Color(0xFF64748B)))),
          Expanded(child: Text(value)),
        ]),
      );
}
