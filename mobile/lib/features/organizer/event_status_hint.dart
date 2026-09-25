import 'package:flutter/material.dart';
import '../../core/manage_kit.dart';

/// What an event's status means for its organiser: a one-line hint and a tone,
/// shown under the event on the list and on its management page.
class StatusHint {
  final String text;
  final KitTone tone;
  final IconData icon;
  const StatusHint(this.text, this.tone, this.icon);
}

StatusHint? organizerHint(String statut) {
  switch (statut) {
    case 'BROUILLON':
      return const StatusHint(
          'Brouillon : complétez la fiche puis soumettez-la à validation.',
          KitTone.amber,
          Icons.edit_note);
    case 'REFUSE':
      return const StatusHint(
          'Refusé : corrigez la fiche puis soumettez-la à nouveau.',
          KitTone.red,
          Icons.report_gmailerrorred_outlined);
    case 'SOUMIS':
      return const StatusHint(
          'En attente de validation par un administrateur.',
          KitTone.blue,
          Icons.hourglass_top);
    case 'VALIDE':
      return const StatusHint(
          "Validé : vous pouvez publier l'événement.",
          KitTone.green,
          Icons.check_circle_outline);
    case 'SUSPENDU':
      return const StatusHint(
          "Suspendu par l'administration.", KitTone.red, Icons.pause_circle_outline);
    case 'ANNULE':
      return const StatusHint('Événement annulé.', KitTone.slate, Icons.event_busy);
    default:
      return null;
  }
}
