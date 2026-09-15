import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/image_field.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';

/// Create (id == null) or edit an event's core information.
class EventFormScreen extends ConsumerStatefulWidget {
  final String? eventId;
  const EventFormScreen({super.key, this.eventId});

  @override
  ConsumerState<EventFormScreen> createState() => _EventFormScreenState();
}

class _EventFormScreenState extends ConsumerState<EventFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nom = TextEditingController();
  final _sigle = TextEditingController();
  final _descCourte = TextEditingController();
  final _descLongue = TextEditingController();
  final _ville = TextEditingController();
  final _lieu = TextEditingController();
  final _adresse = TextEditingController();
  final _contactEmail = TextEditingController();
  String _contactTel = '';
  final _capacite = TextEditingController();

  String? _categoryId;
  DateTime? _dateDebut;
  DateTime? _dateFin;
  DateTime? _inscriptionDebut;
  DateTime? _inscriptionFin;
  String? _coverUrl;
  String? _logoUrl;
  bool _hasActivities = false;
  bool _standsActifs = false;
  bool _standsParticuliers = false;
  bool _validationInscription = false;

  bool _saving = false;
  late final Future<void> _load;

  bool get _isEdit => widget.eventId != null;

  @override
  void initState() {
    super.initState();
    _load = _prefill();
  }

  Future<void> _prefill() async {
    if (!_isEdit) return;
    final e = await ref
        .read(organizerEventsRepositoryProvider)
        .get(widget.eventId!);
    _nom.text = e.nom;
    _sigle.text = e.sigle ?? '';
    _descCourte.text = e.descriptionCourte ?? '';
    _descLongue.text = e.descriptionDetaillee ?? '';
    _ville.text = e.ville ?? '';
    _lieu.text = e.lieu ?? '';
    _adresse.text = e.adresse ?? '';
    _contactEmail.text = e.contactEmail ?? '';
    _contactTel = e.contactTelephone ?? '';
    _capacite.text = e.capaciteMax?.toString() ?? '';
    _categoryId = e.categoryId;
    _dateDebut = e.dateDebut?.toLocal();
    _dateFin = e.dateFin?.toLocal();
    _inscriptionDebut = e.inscriptionDebut?.toLocal();
    _inscriptionFin = e.inscriptionFin?.toLocal();
    _coverUrl = e.coverUrl;
    _logoUrl = e.logoUrl;
    _hasActivities = e.hasActivities;
    _standsActifs = e.standsActifs;
    _standsParticuliers = e.standsParticuliers;
    _validationInscription = e.validationInscription;
  }

  @override
  void dispose() {
    for (final c in [
      _nom, _sigle, _descCourte, _descLongue, _ville, _lieu, _adresse,
      _contactEmail, _capacite
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<DateTime?> _pickDateTime(DateTime? initial) async {
    final now = DateTime.now();
    final date = await showDatePicker(
      context: context,
      initialDate: initial ?? now,
      firstDate: DateTime(now.year - 2),
      lastDate: DateTime(now.year + 5),
    );
    if (date == null || !mounted) return null;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initial ?? now),
    );
    if (time == null) return null;
    return DateTime(date.year, date.month, date.day, time.hour, time.minute);
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    if (_dateDebut == null || _dateFin == null) {
      showSnack(context, 'Renseignez les dates de début et de fin.', error: true);
      return;
    }
    if (_dateFin!.isBefore(_dateDebut!)) {
      showSnack(context, 'La date de fin doit suivre la date de début.', error: true);
      return;
    }
    setState(() => _saving = true);
    final body = {
      'nom': _nom.text.trim(),
      if (_sigle.text.trim().isNotEmpty) 'sigle': _sigle.text.trim(),
      if (_categoryId != null) 'categoryId': _categoryId,
      if (_descCourte.text.trim().isNotEmpty)
        'descriptionCourte': _descCourte.text.trim(),
      if (_descLongue.text.trim().isNotEmpty)
        'descriptionDetaillee': _descLongue.text.trim(),
      'dateDebut': _dateDebut!.toUtc().toIso8601String(),
      'dateFin': _dateFin!.toUtc().toIso8601String(),
      if (_ville.text.trim().isNotEmpty) 'ville': _ville.text.trim(),
      if (_lieu.text.trim().isNotEmpty) 'lieu': _lieu.text.trim(),
      if (_adresse.text.trim().isNotEmpty) 'adresse': _adresse.text.trim(),
      if (_contactEmail.text.trim().isNotEmpty)
        'contactEmail': _contactEmail.text.trim(),
      if (_contactTel.trim().isNotEmpty)
        'contactTelephone': _contactTel.trim(),
      if (_capacite.text.trim().isNotEmpty)
        'capaciteMax': int.tryParse(_capacite.text.trim()),
      if (_coverUrl != null) 'coverUrl': _coverUrl,
      if (_logoUrl != null) 'logoUrl': _logoUrl,
      'hasActivities': _hasActivities,
      'standsActifs': _standsActifs,
      'standsParticuliers': _standsParticuliers,
      'validationInscription': _validationInscription,
      if (_inscriptionDebut != null)
        'inscriptionDebut': _inscriptionDebut!.toUtc().toIso8601String(),
      if (_inscriptionFin != null)
        'inscriptionFin': _inscriptionFin!.toUtc().toIso8601String(),
    };
    try {
      final repo = ref.read(organizerEventsRepositoryProvider);
      final e = _isEdit
          ? await repo.update(widget.eventId!, body)
          : await repo.create(body);
      if (!mounted) return;
      showSnack(context, _isEdit ? 'Événement mis à jour.' : 'Événement créé.');
      if (_isEdit) {
        context.pop();
      } else {
        context.pushReplacement('/mes-evenements/${e.id}');
      }
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cats = ref.watch(eventCategoriesProvider).valueOrNull ?? const [];
    return Scaffold(
      appBar: AppBar(
        title: Text(_isEdit ? 'Modifier l\'événement' : 'Nouvel événement'),
      ),
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
                _text(_nom, 'Nom de l\'événement *', required: true),
                _text(_sigle, 'Sigle'),
                DropdownButtonFormField<String>(
                  value: _categoryId,
                  decoration: const InputDecoration(labelText: 'Catégorie'),
                  items: cats
                      .map((c) => DropdownMenuItem(
                          value: c.id, child: Text(c.nom)))
                      .toList(),
                  onChanged: (v) => setState(() => _categoryId = v),
                ),
                const SizedBox(height: 12),
                _dateTile('Début *', _dateDebut,
                    (d) => setState(() => _dateDebut = d)),
                _dateTile(
                    'Fin *', _dateFin, (d) => setState(() => _dateFin = d)),
                const SizedBox(height: 12),
                _text(_ville, 'Ville'),
                _text(_lieu, 'Lieu'),
                _text(_adresse, 'Adresse'),
                _text(_capacite, 'Capacité maximale',
                    keyboard: TextInputType.number),
                _text(_contactEmail, 'E-mail de contact',
                    keyboard: TextInputType.emailAddress),
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: PhoneField(
                    initialValue: _contactTel,
                    onChanged: (v) => _contactTel = v,
                  ),
                ),
                _text(_descCourte, 'Description courte'),
                _text(_descLongue, 'Description détaillée', lines: 4),
                const SizedBox(height: 8),
                ImageField(
                  label: 'Image de couverture',
                  value: _coverUrl,
                  folder: 'evenements',
                  onChanged: (v) => setState(() => _coverUrl = v),
                ),
                const SizedBox(height: 12),
                ImageField(
                  label: 'Logo',
                  value: _logoUrl,
                  folder: 'evenements',
                  height: 90,
                  onChanged: (v) => setState(() => _logoUrl = v),
                ),
                const SizedBox(height: 16),
                _dateTile('Ouverture des inscriptions', _inscriptionDebut,
                    (d) => setState(() => _inscriptionDebut = d), clearable: true),
                _dateTile('Clôture des inscriptions', _inscriptionFin,
                    (d) => setState(() => _inscriptionFin = d), clearable: true),
                const SizedBox(height: 8),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Cet événement contient plusieurs activités'),
                  value: _hasActivities,
                  onChanged: (v) => setState(() => _hasActivities = v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Réservation de stands'),
                  value: _standsActifs,
                  onChanged: (v) => setState(() => _standsActifs = v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Valider chaque inscription manuellement'),
                  value: _validationInscription,
                  onChanged: (v) => setState(() => _validationInscription = v),
                ),
                if (_standsActifs)
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Stands : autoriser les particuliers'),
                    subtitle: const Text(
                        'Réservation possible sans structure vérifiée'),
                    value: _standsParticuliers,
                    onChanged: (v) => setState(() => _standsParticuliers = v),
                  ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _saving ? null : _save,
                  child: Text(_saving
                      ? 'Enregistrement…'
                      : (_isEdit ? 'Enregistrer' : 'Créer l\'événement')),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _text(TextEditingController c, String label,
      {bool required = false,
      TextInputType? keyboard,
      int lines = 1}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: c,
        keyboardType: keyboard,
        maxLines: lines,
        decoration: InputDecoration(labelText: label),
        validator: required
            ? (v) => (v == null || v.trim().isEmpty) ? 'Requis' : null
            : null,
      ),
    );
  }

  Widget _dateTile(String label, DateTime? value, ValueChanged<DateTime?> onSet,
      {bool clearable = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: ListTile(
        contentPadding: EdgeInsets.zero,
        leading: const Icon(Icons.event),
        title: Text(label),
        subtitle: Text(value == null ? 'Non défini' : Fmt.dateTime(value)),
        trailing: clearable && value != null
            ? IconButton(
                icon: const Icon(Icons.clear),
                onPressed: () => onSet(null),
              )
            : const Icon(Icons.chevron_right),
        onTap: () async {
          final d = await _pickDateTime(value);
          if (d != null) onSet(d);
        },
      ),
    );
  }
}
