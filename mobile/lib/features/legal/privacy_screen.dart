import 'package:flutter/material.dart';

/// Legal notice and privacy policy (loi n°001-2021/AN du 30 mars 2021).
/// Same wording as the web page /confidentialite.
class PrivacyScreen extends StatelessWidget {
  const PrivacyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final h = Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600);
    Widget section(String title, String body) => Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: h),
            const SizedBox(height: 4),
            Text(body),
          ]),
        );
    return Scaffold(
      appBar: AppBar(title: const Text('Confidentialité')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        section('Responsable du traitement',
            "La Plateforme Nationale des Événements (PNE) est opérée par le Ministère de la Transition "
                'Digitale, des Postes et des Communications Électroniques (MTDPCE), Burkina Faso. Le '
                "traitement est déclaré auprès de la Commission de l'Informatique et des Libertés (CIL)."),
        section('Données collectées',
            '• Identité : nom et/ou prénom, selon l\'événement.\n'
                '• Coordonnées : numéro de téléphone, adresse e-mail (facultative).\n'
                '• Participation : billets, sessions choisies, heures d\'entrée et de sortie.\n'
                '• Comptes : journal de connexion (date, adresse IP) à des fins de sécurité.'),
        section('Finalités',
            "Émettre vos billets, contrôler l'accès aux sessions, vous informer d'un changement de "
                'programme et produire des statistiques de fréquentation anonymes. Vos données ne sont '
                'ni vendues ni utilisées à des fins commerciales.'),
        section('Durée de conservation',
            "Les données des participants sont anonymisées automatiquement 12 mois après la fin de "
                "l'événement ; seules les statistiques agrégées sont conservées."),
        section('Vos droits',
            'Conformément à la loi n°001-2021/AN du 30 mars 2021, vous disposez d\'un droit d\'accès, '
                'de rectification, d\'opposition et de suppression. Adressez votre demande à '
                "l'organisateur de l'événement ou au MTDPCE. Vous pouvez également saisir la CIL."),
        section('Sécurité',
            'Les échanges sont chiffrés (HTTPS), les accès d\'administration sont nominatifs, protégés '
                'par double authentification, et journalisés.'),
      ]),
    );
  }
}
