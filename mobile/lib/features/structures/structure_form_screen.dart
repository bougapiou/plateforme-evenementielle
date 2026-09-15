import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import 'structures_screen.dart';

class StructureFormScreen extends ConsumerStatefulWidget {
  final String? structureId; // null = création
  const StructureFormScreen({super.key, this.structureId});

  @override
  ConsumerState<StructureFormScreen> createState() =>
      _StructureFormScreenState();
}

class _StructureFormScreenState extends ConsumerState<StructureFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _raisonSociale = TextEditingController();
  final _sigle = TextEditingController();
  final _secteur = TextEditingController();
  final _rccm = TextEditingController();
  final _ifu = TextEditingController();
  final _adresse = TextEditingController();
  final _ville = TextEditingController();
  String _telephone = '';
  final _email = TextEditingController();
  final _siteWeb = TextEditingController();
  final _description = TextEditingController();
  String _type = 'ENTREPRISE';
  bool _saving = false;
  late final Future<void> _load;

  bool get _isEdit => widget.structureId != null;

  @override
  void initState() {
    super.initState();
    _load = _prefill();
  }

  Future<void> _prefill() async {
    if (!_isEdit) return;
    final s = await ref.read(structuresRepositoryProvider).get(widget.structureId!);
    _raisonSociale.text = s.raisonSociale;
    _sigle.text = s.sigle ?? '';
    _secteur.text = s.secteurActivite ?? '';
    _rccm.text = s.rccm ?? '';
    _ifu.text = s.ifu ?? '';
    _adresse.text = s.adresse ?? '';
    _ville.text = s.ville ?? '';
    _telephone = s.telephone ?? '';
    _email.text = s.email ?? '';
    _siteWeb.text = s.siteWeb ?? '';
    _description.text = s.description ?? '';
    _type = s.typeStructure;
  }

  @override
  void dispose() {
    for (final c in [
      _raisonSociale, _sigle, _secteur, _rccm, _ifu, _adresse,
      _ville, _email, _siteWeb, _description
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Map<String, dynamic> _body() => {
        'raisonSociale': _raisonSociale.text.trim(),
        if (_sigle.text.trim().isNotEmpty) 'sigle': _sigle.text.trim(),
        'typeStructure': _type,
        if (_secteur.text.trim().isNotEmpty) 'secteurActivite': _secteur.text.trim(),
        if (_rccm.text.trim().isNotEmpty) 'rccm': _rccm.text.trim(),
        if (_ifu.text.trim().isNotEmpty) 'ifu': _ifu.text.trim(),
        if (_adresse.text.trim().isNotEmpty) 'adresse': _adresse.text.trim(),
        if (_ville.text.trim().isNotEmpty) 'ville': _ville.text.trim(),
        if (_telephone.trim().isNotEmpty) 'telephone': _telephone.trim(),
        if (_email.text.trim().isNotEmpty) 'email': _email.text.trim(),
        if (_siteWeb.text.trim().isNotEmpty) 'siteWeb': _siteWeb.text.trim(),
        if (_description.text.trim().isNotEmpty) 'description': _description.text.trim(),
      };

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final repo = ref.read(structuresRepositoryProvider);
      final s = _isEdit
          ? await repo.update(widget.structureId!, _body())
          : await repo.create(_body());
      ref.invalidate(myStructuresProvider);
      if (mounted) {
        showSnack(context,
            _isEdit ? 'Structure mise à jour.' : 'Structure créée.');
        if (_isEdit) {
          context.pop();
        } else {
          context.pushReplacement('/structures/${s.id}');
        }
      }
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
          title: Text(_isEdit ? 'Modifier la structure' : 'Nouvelle structure')),
      body: FutureBuilder<void>(
        future: _load,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          return Form(
            key: _formKey,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                TextFormField(
                  controller: _raisonSociale,
                  decoration:
                      const InputDecoration(labelText: 'Raison sociale *'),
                  validator: (v) =>
                      (v == null || v.trim().isEmpty) ? 'Requis' : null,
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: _type,
                  decoration:
                      const InputDecoration(labelText: 'Type de structure *'),
                  items: structureTypeLabels.entries
                      .map((e) => DropdownMenuItem(
                          value: e.key, child: Text(e.value)))
                      .toList(),
                  onChanged: (v) => setState(() => _type = v ?? 'ENTREPRISE'),
                ),
                const SizedBox(height: 12),
                _field(_sigle, 'Sigle'),
                _field(_secteur, 'Secteur d\'activité'),
                _field(_rccm, 'RCCM'),
                _field(_ifu, 'IFU'),
                _field(_adresse, 'Adresse'),
                _field(_ville, 'Ville'),
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: PhoneField(
                    initialValue: _telephone,
                    onChanged: (v) => _telephone = v,
                  ),
                ),
                _field(_email, 'E-mail', keyboard: TextInputType.emailAddress),
                _field(_siteWeb, 'Site web'),
                _field(_description, 'Description', lines: 3),
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _saving ? null : _save,
                  child: Text(_saving
                      ? 'Enregistrement…'
                      : (_isEdit ? 'Enregistrer' : 'Créer la structure')),
                ),
                const SizedBox(height: 8),
                Text(
                  'Une structure nouvellement créée est « en attente » de '
                  'vérification par un administrateur. Vous pouvez déjà l\'utiliser '
                  'pour vos inscriptions.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _field(TextEditingController c, String label,
      {TextInputType? keyboard, int lines = 1}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: c,
        keyboardType: keyboard,
        maxLines: lines,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }
}
