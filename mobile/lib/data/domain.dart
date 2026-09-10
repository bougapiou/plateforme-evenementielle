import '../core/format.dart';

/// Domain models mirroring the backend REST DTOs. JSON-only, no logic.

class Paged<T> {
  final List<T> content;
  final int page;
  final int totalPages;
  final int totalElements;
  final bool last;

  Paged({
    required this.content,
    required this.page,
    required this.totalPages,
    required this.totalElements,
    required this.last,
  });

  factory Paged.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) item,
  ) =>
      Paged(
        content: (json['content'] as List<dynamic>? ?? [])
            .map((e) => item(e as Map<String, dynamic>))
            .toList(),
        page: json['page'] as int? ?? 0,
        totalPages: json['totalPages'] as int? ?? 0,
        totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
        last: json['last'] as bool? ?? true,
      );
}

class EventCategory {
  final String id;
  final String nom;
  final String slug;
  final String? icone;

  EventCategory({required this.id, required this.nom, required this.slug, this.icone});

  factory EventCategory.fromJson(Map<String, dynamic> j) => EventCategory(
        id: j['id'] as String,
        nom: j['nom'] as String,
        slug: j['slug'] as String,
        icone: j['icone'] as String?,
      );
}

class EventSummary {
  final String id;
  final String nom;
  final String? sigle;
  final String slug;
  final String? descriptionCourte;
  final String? categoryNom;
  final String? coverUrl;
  final String? logoUrl;
  final DateTime? dateDebut;
  final DateTime? dateFin;
  final String? ville;
  final String? lieu;
  final String? organizerNom;
  final bool standsActifs;
  final String statut;

  EventSummary({
    required this.id,
    required this.nom,
    required this.slug,
    this.sigle,
    this.descriptionCourte,
    this.categoryNom,
    this.coverUrl,
    this.logoUrl,
    this.dateDebut,
    this.dateFin,
    this.ville,
    this.lieu,
    this.organizerNom,
    this.standsActifs = false,
    this.statut = '',
  });

  factory EventSummary.fromJson(Map<String, dynamic> j) => EventSummary(
        id: j['id'] as String,
        nom: j['nom'] as String,
        slug: j['slug'] as String,
        sigle: j['sigle'] as String?,
        descriptionCourte: j['descriptionCourte'] as String?,
        categoryNom: j['categoryNom'] as String?,
        coverUrl: j['coverUrl'] as String?,
        logoUrl: j['logoUrl'] as String?,
        dateDebut: parseDate(j['dateDebut']),
        dateFin: parseDate(j['dateFin']),
        ville: j['ville'] as String?,
        lieu: j['lieu'] as String?,
        organizerNom: j['organizerNom'] as String?,
        standsActifs: j['standsActifs'] as bool? ?? false,
        statut: j['statut'] as String? ?? '',
      );
}

class Activity {
  final String id;
  final String titre;
  final String? description;
  final String? typeActivite;
  /// SANS_BILLET | GRATUIT | PAYANT
  final String acces;
  final DateTime? dateDebut;
  final DateTime? dateFin;
  final String? salle;
  final String? lieu;
  final String? intervenant;
  final String? moderateur;
  final int? capacite;
  final String? imageUrl;

  Activity({
    required this.id,
    required this.titre,
    this.description,
    this.typeActivite,
    this.acces = 'SANS_BILLET',
    this.dateDebut,
    this.dateFin,
    this.salle,
    this.lieu,
    this.intervenant,
    this.moderateur,
    this.capacite,
    this.imageUrl,
  });

  bool get gratuit => acces == 'GRATUIT';
  bool get payant => acces == 'PAYANT';

  factory Activity.fromJson(Map<String, dynamic> j) => Activity(
        id: j['id'] as String,
        titre: j['titre'] as String,
        description: j['description'] as String?,
        typeActivite: j['typeActivite'] as String?,
        acces: j['acces'] as String? ?? 'SANS_BILLET',
        dateDebut: parseDate(j['dateDebut']),
        dateFin: parseDate(j['dateFin']),
        salle: j['salle'] as String?,
        lieu: j['lieu'] as String?,
        intervenant: j['intervenant'] as String?,
        moderateur: j['moderateur'] as String?,
        capacite: j['capacite'] as int?,
        imageUrl: j['imageUrl'] as String?,
      );
}

/// Full editable event (from `GET /api/events/{id}` — organiser view).
class EventFull {
  final String id;
  final String nom;
  final String? sigle;
  final String slug;
  final String? descriptionCourte;
  final String? descriptionDetaillee;
  final String? categoryId;
  final String? logoUrl;
  final String? coverUrl;
  final DateTime? dateDebut;
  final DateTime? dateFin;
  final String? lieu;
  final String? adresse;
  final String? ville;
  final String? contactEmail;
  final String? contactTelephone;
  final String? siteWeb;
  final String? conditionsParticipation;
  final int? capaciteMax;
  final bool hasActivities;
  final bool standsActifs;
  final bool standsParticuliers;
  final bool validationInscription;
  final DateTime? inscriptionDebut;
  final DateTime? inscriptionFin;
  final String statut;
  final String? motifRefus;

  EventFull({
    required this.id,
    required this.nom,
    required this.slug,
    this.sigle,
    this.descriptionCourte,
    this.descriptionDetaillee,
    this.categoryId,
    this.logoUrl,
    this.coverUrl,
    this.dateDebut,
    this.dateFin,
    this.lieu,
    this.adresse,
    this.ville,
    this.contactEmail,
    this.contactTelephone,
    this.siteWeb,
    this.conditionsParticipation,
    this.capaciteMax,
    this.hasActivities = false,
    this.standsActifs = false,
    this.standsParticuliers = false,
    this.validationInscription = false,
    this.inscriptionDebut,
    this.inscriptionFin,
    this.statut = 'BROUILLON',
    this.motifRefus,
  });

  bool get modifiable =>
      statut == 'BROUILLON' || statut == 'REFUSE' || statut == 'VALIDE';
  bool get soumissible => statut == 'BROUILLON' || statut == 'REFUSE';

  factory EventFull.fromJson(Map<String, dynamic> j) => EventFull(
        id: j['id'] as String,
        nom: j['nom'] as String,
        slug: j['slug'] as String? ?? '',
        sigle: j['sigle'] as String?,
        descriptionCourte: j['descriptionCourte'] as String?,
        descriptionDetaillee: j['descriptionDetaillee'] as String?,
        categoryId: j['categoryId'] as String?,
        logoUrl: j['logoUrl'] as String?,
        coverUrl: j['coverUrl'] as String?,
        dateDebut: parseDate(j['dateDebut']),
        dateFin: parseDate(j['dateFin']),
        lieu: j['lieu'] as String?,
        adresse: j['adresse'] as String?,
        ville: j['ville'] as String?,
        contactEmail: j['contactEmail'] as String?,
        contactTelephone: j['contactTelephone'] as String?,
        siteWeb: j['siteWeb'] as String?,
        conditionsParticipation: j['conditionsParticipation'] as String?,
        capaciteMax: j['capaciteMax'] as int?,
        hasActivities: j['hasActivities'] as bool? ?? false,
        standsActifs: j['standsActifs'] as bool? ?? false,
        standsParticuliers: j['standsParticuliers'] as bool? ?? false,
        validationInscription: j['validationInscription'] as bool? ?? false,
        inscriptionDebut: parseDate(j['inscriptionDebut']),
        inscriptionFin: parseDate(j['inscriptionFin']),
        statut: j['statut'] as String? ?? 'BROUILLON',
        motifRefus: j['motifRefus'] as String?,
      );
}

const activityTypes = [
  'CEREMONIE', 'CONFERENCE', 'PANEL', 'ATELIER', 'FORMATION',
  'TABLE_RONDE', 'NETWORKING', 'PAUSE', 'SPECTACLE', 'AUTRE',
];

const partnerLevels = [
  'PLATINE', 'OR', 'ARGENT', 'BRONZE',
  'PARTENAIRE', 'PARTENAIRE_MEDIA', 'PARTENAIRE_INSTITUTIONNEL',
];

class Speaker {
  final String id;
  final String nom;
  final String? titre;
  final String? organisation;
  final String? bio;
  final String? photoUrl;

  Speaker({
    required this.id,
    required this.nom,
    this.titre,
    this.organisation,
    this.bio,
    this.photoUrl,
  });

  factory Speaker.fromJson(Map<String, dynamic> j) => Speaker(
        id: j['id'] as String,
        nom: j['nom'] as String,
        titre: j['titre'] as String?,
        organisation: j['organisation'] as String?,
        bio: j['bio'] as String?,
        photoUrl: j['photoUrl'] as String?,
      );
}

class Partner {
  final String id;
  final String nom;
  final String? niveau;
  final String? logoUrl;

  Partner({required this.id, required this.nom, this.niveau, this.logoUrl});

  factory Partner.fromJson(Map<String, dynamic> j) => Partner(
        id: j['id'] as String,
        nom: j['nom'] as String,
        niveau: j['niveau'] as String?,
        logoUrl: j['logoUrl'] as String?,
      );
}

class EventDetail {
  final String id;
  final String nom;
  final String? sigle;
  final String slug;
  final String? descriptionCourte;
  final String? descriptionDetaillee;
  final String? categoryNom;
  final String? coverUrl;
  final String? logoUrl;
  final DateTime? dateDebut;
  final DateTime? dateFin;
  final String? lieu;
  final String? adresse;
  final String? ville;
  final String? contactEmail;
  final String? contactTelephone;
  final String? siteWeb;
  final String? conditionsParticipation;
  final bool hasActivities;
  final bool standsActifs;
  final bool standsParticuliers;
  final DateTime? inscriptionDebut;
  final DateTime? inscriptionFin;
  final String statut;
  final String? organizerNom;
  final List<Activity> programme;
  final List<Speaker> intervenants;
  final List<Partner> partenaires;

  EventDetail({
    required this.id,
    required this.nom,
    required this.slug,
    this.sigle,
    this.descriptionCourte,
    this.descriptionDetaillee,
    this.categoryNom,
    this.coverUrl,
    this.logoUrl,
    this.dateDebut,
    this.dateFin,
    this.lieu,
    this.adresse,
    this.ville,
    this.contactEmail,
    this.contactTelephone,
    this.siteWeb,
    this.conditionsParticipation,
    this.hasActivities = false,
    this.standsActifs = false,
    this.standsParticuliers = false,
    this.inscriptionDebut,
    this.inscriptionFin,
    this.statut = '',
    this.organizerNom,
    this.programme = const [],
    this.intervenants = const [],
    this.partenaires = const [],
  });

  bool get inscriptionsOuvertes =>
      statut == 'PUBLIE' || statut == 'INSCRIPTIONS_OUVERTES';

  bool get termine => statut == 'TERMINE';

  factory EventDetail.fromJson(Map<String, dynamic> j) => EventDetail(
        id: j['id'] as String,
        nom: j['nom'] as String,
        slug: j['slug'] as String,
        sigle: j['sigle'] as String?,
        descriptionCourte: j['descriptionCourte'] as String?,
        descriptionDetaillee: j['descriptionDetaillee'] as String?,
        categoryNom: j['categoryNom'] as String?,
        coverUrl: j['coverUrl'] as String?,
        logoUrl: j['logoUrl'] as String?,
        dateDebut: parseDate(j['dateDebut']),
        dateFin: parseDate(j['dateFin']),
        lieu: j['lieu'] as String?,
        adresse: j['adresse'] as String?,
        ville: j['ville'] as String?,
        contactEmail: j['contactEmail'] as String?,
        contactTelephone: j['contactTelephone'] as String?,
        siteWeb: j['siteWeb'] as String?,
        conditionsParticipation: j['conditionsParticipation'] as String?,
        hasActivities: j['hasActivities'] as bool? ?? false,
        standsActifs: j['standsActifs'] as bool? ?? false,
        standsParticuliers: j['standsParticuliers'] as bool? ?? false,
        inscriptionDebut: parseDate(j['inscriptionDebut']),
        inscriptionFin: parseDate(j['inscriptionFin']),
        statut: j['statut'] as String? ?? '',
        organizerNom: j['organizerNom'] as String?,
        programme: (j['programme'] as List<dynamic>? ?? [])
            .map((e) => Activity.fromJson(e as Map<String, dynamic>))
            .toList(),
        intervenants: (j['intervenants'] as List<dynamic>? ?? [])
            .map((e) => Speaker.fromJson(e as Map<String, dynamic>))
            .toList(),
        partenaires: (j['partenaires'] as List<dynamic>? ?? [])
            .map((e) => Partner.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class EventTicketType {
  final String id;
  final String nom;
  final String? description;
  final num prixMontant;
  final String devise;
  final String prixFormatte;
  final String portee;
  final int quantiteTotale;
  final int quantiteRestante;
  final int limiteParUtilisateur;
  final bool actif;
  final bool enVente;
  final List<String> activites;
  final List<String> activiteIds;

  EventTicketType({
    required this.id,
    required this.nom,
    this.description,
    required this.prixMontant,
    required this.devise,
    required this.prixFormatte,
    required this.portee,
    this.quantiteTotale = 0,
    required this.quantiteRestante,
    required this.limiteParUtilisateur,
    this.actif = true,
    required this.enVente,
    this.activites = const [],
    this.activiteIds = const [],
  });

  bool get gratuit => prixMontant == 0;

  factory EventTicketType.fromJson(Map<String, dynamic> j) {
    final acts = (j['activites'] as List<dynamic>? ?? [])
        .map((e) => e as Map<String, dynamic>)
        .toList();
    return EventTicketType(
      id: j['id'] as String,
      nom: j['nom'] as String,
      description: j['description'] as String?,
      prixMontant: (j['prixMontant'] as num?) ?? 0,
      devise: j['devise'] as String? ?? 'XOF',
      prixFormatte: j['prixFormatte'] as String? ?? '',
      portee: j['portee'] as String? ?? 'EVENEMENT',
      quantiteTotale: j['quantiteTotale'] as int? ?? 0,
      quantiteRestante: j['quantiteRestante'] as int? ?? 0,
      limiteParUtilisateur: j['limiteParUtilisateur'] as int? ?? 0,
      actif: j['actif'] as bool? ?? true,
      enVente: j['enVente'] as bool? ?? false,
      activites: acts
          .map((a) => a['titre'] as String? ?? '')
          .where((s) => s.isNotEmpty)
          .toList(),
      activiteIds: acts.map((a) => a['id'] as String? ?? '').where((s) => s.isNotEmpty).toList(),
    );
  }
}

class StandType {
  final String id;
  final String nom;
  final String? description;
  final String? dimensions;
  final num prixMontant;
  final String devise;
  final String prixFormatte;
  final int quantiteTotale;
  final int quantiteRestante;
  final String? equipements;
  final String? conditions;

  StandType({
    required this.id,
    required this.nom,
    this.description,
    this.dimensions,
    required this.prixMontant,
    required this.devise,
    required this.prixFormatte,
    this.quantiteTotale = 0,
    required this.quantiteRestante,
    this.equipements,
    this.conditions,
  });

  factory StandType.fromJson(Map<String, dynamic> j) => StandType(
        id: j['id'] as String,
        nom: j['nom'] as String,
        description: j['description'] as String?,
        dimensions: j['dimensions'] as String?,
        prixMontant: (j['prixMontant'] as num?) ?? 0,
        devise: j['devise'] as String? ?? 'XOF',
        prixFormatte: j['prixFormatte'] as String? ?? '',
        quantiteTotale: j['quantiteTotale'] as int? ?? 0,
        conditions: j['conditions'] as String?,
        quantiteRestante: j['quantiteRestante'] as int? ?? 0,
        equipements: j['equipements'] as String?,
      );
}

class Stand {
  final String id;
  final String numero;
  final String standTypeNom;
  final String statut;
  final bool disponible;
  final num prixMontant;
  final String prixFormatte;

  Stand({
    required this.id,
    required this.numero,
    required this.standTypeNom,
    required this.statut,
    required this.disponible,
    required this.prixMontant,
    required this.prixFormatte,
  });

  factory Stand.fromJson(Map<String, dynamic> j) => Stand(
        id: j['id'] as String,
        numero: j['numero'] as String? ?? '',
        standTypeNom: j['standTypeNom'] as String? ?? '',
        statut: j['statut'] as String? ?? '',
        disponible: j['disponible'] as bool? ?? false,
        prixMontant: (j['prixMontant'] as num?) ?? 0,
        prixFormatte: j['prixFormatte'] as String? ?? '',
      );
}

class TicketOrder {
  final String id;
  final String reference;
  final String eventNom;
  final String statut;
  final num montantTotal;
  final String devise;
  final String montantFormatte;
  final DateTime? expireLe;
  final DateTime? createdAt;
  final List<OrderLine> lignes;

  TicketOrder({
    required this.id,
    required this.reference,
    required this.eventNom,
    required this.statut,
    required this.montantTotal,
    required this.devise,
    required this.montantFormatte,
    this.expireLe,
    this.createdAt,
    this.lignes = const [],
  });

  bool get enAttente => statut == 'EN_ATTENTE';
  bool get payee => statut == 'PAYEE';

  factory TicketOrder.fromJson(Map<String, dynamic> j) => TicketOrder(
        id: j['id'] as String,
        reference: j['reference'] as String? ?? '',
        eventNom: j['eventNom'] as String? ?? '',
        statut: j['statut'] as String? ?? '',
        montantTotal: (j['montantTotal'] as num?) ?? 0,
        devise: j['devise'] as String? ?? 'XOF',
        montantFormatte: j['montantFormatte'] as String? ?? '',
        expireLe: parseDate(j['expireLe']),
        createdAt: parseDate(j['createdAt']),
        lignes: (j['lignes'] as List<dynamic>? ?? [])
            .map((e) => OrderLine.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class OrderLine {
  final String ticketNom;
  final int quantite;
  final num prixUnitaire;

  OrderLine({required this.ticketNom, required this.quantite, required this.prixUnitaire});

  factory OrderLine.fromJson(Map<String, dynamic> j) => OrderLine(
        ticketNom: j['ticketNom'] as String? ?? '',
        quantite: j['quantite'] as int? ?? 0,
        prixUnitaire: (j['prixUnitaire'] as num?) ?? 0,
      );
}

class Ticket {
  final String id;
  final String numero;
  final String eventId;
  final String eventNom;
  final DateTime? eventDateDebut;
  final String? lieu;
  final String? categorieNom;
  final String? participantNom;
  final String statut; // EMISE / UTILISE / ANNULE
  final String? orderReference;
  final String qrImageUrl;
  final String pdfUrl;

  Ticket({
    required this.id,
    required this.numero,
    required this.eventId,
    required this.eventNom,
    this.eventDateDebut,
    this.lieu,
    this.categorieNom,
    this.participantNom,
    required this.statut,
    this.orderReference,
    required this.qrImageUrl,
    required this.pdfUrl,
  });

  bool get utilise => statut == 'UTILISE';
  bool get valide => statut == 'EMISE';

  factory Ticket.fromJson(Map<String, dynamic> j) => Ticket(
        id: j['id'] as String,
        numero: j['numero'] as String? ?? '',
        eventId: j['eventId'] as String? ?? '',
        eventNom: j['eventNom'] as String? ?? '',
        eventDateDebut: parseDate(j['eventDateDebut']),
        lieu: j['lieu'] as String?,
        categorieNom: j['categorieNom'] as String?,
        participantNom: j['participantNom'] as String?,
        statut: j['statut'] as String? ?? '',
        orderReference: j['orderReference'] as String?,
        qrImageUrl: j['qrImageUrl'] as String? ?? '',
        pdfUrl: j['pdfUrl'] as String? ?? '',
      );
}

class Registration {
  final String id;
  final String reference;
  final String eventId;
  final String eventNom;
  final String type;
  final String statut;
  final String? structureNom;
  final String? contactNom;
  final String? contactEmail;
  final String? contactTelephone;
  final int nombreParticipants;
  final String? informations;
  final String? motifRefus;
  final String? ticketOrderId;
  final String? ticketOrderStatut;
  final List<ParticipantView> participants;
  final DateTime? createdAt;

  Registration({
    required this.id,
    required this.reference,
    this.eventId = '',
    required this.eventNom,
    required this.type,
    required this.statut,
    this.structureNom,
    this.contactNom,
    this.contactEmail,
    this.contactTelephone,
    required this.nombreParticipants,
    this.informations,
    this.motifRefus,
    this.ticketOrderId,
    this.ticketOrderStatut,
    this.participants = const [],
    this.createdAt,
  });

  bool get annulable => statut == 'EN_ATTENTE' || statut == 'CONFIRMEE';

  factory Registration.fromJson(Map<String, dynamic> j) => Registration(
        id: j['id'] as String,
        reference: j['reference'] as String? ?? '',
        eventId: j['eventId'] as String? ?? '',
        eventNom: j['eventNom'] as String? ?? '',
        type: j['type'] as String? ?? '',
        statut: j['statut'] as String? ?? '',
        structureNom: j['structureNom'] as String?,
        contactNom: j['contactNom'] as String?,
        contactEmail: j['contactEmail'] as String?,
        contactTelephone: j['contactTelephone'] as String?,
        nombreParticipants: j['nombreParticipants'] as int? ?? 0,
        informations: j['informations'] as String?,
        motifRefus: j['motifRefus'] as String?,
        ticketOrderId: j['ticketOrderId'] as String?,
        ticketOrderStatut: j['ticketOrderStatut'] as String?,
        participants: (j['participants'] as List<dynamic>? ?? [])
            .map((e) => ParticipantView.fromJson(e as Map<String, dynamic>))
            .toList(),
        createdAt: parseDate(j['createdAt']),
      );
}

class StandReservation {
  final String id;
  final String reference;
  final String eventNom;
  final String standNumero;
  final String standTypeNom;
  final String statut;
  final num montant;
  final String montantFormatte;
  final DateTime? holdExpireLe;
  final DateTime? createdAt;

  StandReservation({
    required this.id,
    required this.reference,
    required this.eventNom,
    required this.standNumero,
    required this.standTypeNom,
    required this.statut,
    required this.montant,
    required this.montantFormatte,
    this.holdExpireLe,
    this.createdAt,
  });

  bool get aPayer => statut == 'RESERVE_TEMP' || statut == 'ATTENTE_PAIEMENT';

  factory StandReservation.fromJson(Map<String, dynamic> j) => StandReservation(
        id: j['id'] as String,
        reference: j['reference'] as String? ?? '',
        eventNom: j['eventNom'] as String? ?? '',
        standNumero: j['standNumero'] as String? ?? '',
        standTypeNom: j['standTypeNom'] as String? ?? '',
        statut: j['statut'] as String? ?? '',
        montant: (j['montant'] as num?) ?? 0,
        montantFormatte: j['montantFormatte'] as String? ?? '',
        holdExpireLe: parseDate(j['holdExpireLe']),
        createdAt: parseDate(j['createdAt']),
      );
}

class Payment {
  final String id;
  final String reference;
  final String provider;
  final String? moyen;
  final String targetType;
  final num montant;
  final String montantFormatte;
  final String statut;
  final String? paymentUrl;
  final DateTime? createdAt;

  Payment({
    required this.id,
    required this.reference,
    required this.provider,
    this.moyen,
    required this.targetType,
    required this.montant,
    required this.montantFormatte,
    required this.statut,
    this.paymentUrl,
    this.createdAt,
  });

  bool get reussi => statut == 'REUSSI';

  factory Payment.fromJson(Map<String, dynamic> j) => Payment(
        id: j['id'] as String,
        reference: j['reference'] as String? ?? '',
        provider: j['provider'] as String? ?? '',
        moyen: j['moyen'] as String?,
        targetType: j['targetType'] as String? ?? '',
        montant: (j['montant'] as num?) ?? 0,
        montantFormatte: j['montantFormatte'] as String? ?? '',
        statut: j['statut'] as String? ?? '',
        paymentUrl: j['paymentUrl'] as String?,
        createdAt: parseDate(j['createdAt']),
      );
}

class Invoice {
  final String id;
  final String numero;
  final String type; // FACTURE / RECU
  final num montant;
  final String? devise;
  final String? montantFormatte;
  final String? clientNom;
  final DateTime? emiseLe;
  final String pdfUrl;

  Invoice({
    required this.id,
    required this.numero,
    required this.type,
    required this.montant,
    this.devise,
    this.montantFormatte,
    this.clientNom,
    this.emiseLe,
    required this.pdfUrl,
  });

  factory Invoice.fromJson(Map<String, dynamic> j) => Invoice(
        id: j['id'] as String,
        numero: (j['numero'] ?? j['reference'] ?? '') as String,
        type: j['type'] as String? ?? 'DOCUMENT',
        montant: (j['montant'] as num?) ?? (j['montantTotal'] as num?) ?? 0,
        devise: j['devise'] as String?,
        montantFormatte: j['montantFormatte'] as String?,
        clientNom: j['clientNom'] as String?,
        emiseLe: parseDate(j['emiseLe'] ?? j['createdAt']),
        pdfUrl: j['pdfUrl'] as String? ?? '/api/invoices/${j['id']}/pdf',
      );
}

class AppNotification {
  final String id;
  final String titre;
  final String contenu;
  final String? lien;
  final bool lu;
  final DateTime? createdAt;

  AppNotification({
    required this.id,
    required this.titre,
    required this.contenu,
    this.lien,
    required this.lu,
    this.createdAt,
  });

  factory AppNotification.fromJson(Map<String, dynamic> j) => AppNotification(
        id: j['id'] as String,
        titre: (j['titre'] ?? j['sujet'] ?? '') as String,
        contenu: (j['contenu'] ?? j['message'] ?? '') as String,
        lien: j['lien'] as String?,
        lu: (j['lu'] ?? j['read'] ?? false) as bool,
        createdAt: parseDate(j['createdAt'] ?? j['envoyeeLe']),
      );
}

class Me {
  final String id;
  final String email;
  final String firstName;
  final String lastName;
  final String fullName;
  final String? phone;
  final String type;
  final String status;
  final List<String> roles;
  final List<String> permissions;

  Me({
    required this.id,
    required this.email,
    required this.firstName,
    required this.lastName,
    required this.fullName,
    this.phone,
    required this.type,
    required this.status,
    this.roles = const [],
    this.permissions = const [],
  });

  factory Me.fromJson(Map<String, dynamic> j) => Me(
        id: j['id'] as String,
        email: j['email'] as String,
        firstName: j['firstName'] as String? ?? '',
        lastName: j['lastName'] as String? ?? '',
        fullName: j['fullName'] as String? ?? '',
        phone: j['phone'] as String?,
        type: j['type'] as String? ?? 'PARTICULIER',
        status: j['status'] as String? ?? 'ACTIF',
        roles: (j['roles'] as List<dynamic>? ?? []).cast<String>(),
        permissions: (j['permissions'] as List<dynamic>? ?? []).cast<String>(),
      );
}

class StructureSummary {
  final String id;
  final String raisonSociale;
  final String? sigle;
  final String typeStructure;
  final String? ville;
  final String statut;

  StructureSummary({
    required this.id,
    required this.raisonSociale,
    this.sigle,
    required this.typeStructure,
    this.ville,
    required this.statut,
  });

  bool get verifiee => statut == 'VERIFIEE';

  factory StructureSummary.fromJson(Map<String, dynamic> j) => StructureSummary(
        id: j['id'] as String,
        raisonSociale: j['raisonSociale'] as String? ?? '',
        sigle: j['sigle'] as String?,
        typeStructure: j['typeStructure'] as String? ?? 'AUTRE',
        ville: j['ville'] as String?,
        statut: j['statut'] as String? ?? 'EN_ATTENTE',
      );
}

class Structure {
  final String id;
  final String raisonSociale;
  final String? sigle;
  final String typeStructure;
  final String? secteurActivite;
  final String? rccm;
  final String? ifu;
  final String? adresse;
  final String? ville;
  final String? pays;
  final String? telephone;
  final String? email;
  final String? siteWeb;
  final String? description;
  final String statut;
  final String? ownerId;
  final int memberCount;
  final String? myRole;

  Structure({
    required this.id,
    required this.raisonSociale,
    this.sigle,
    required this.typeStructure,
    this.secteurActivite,
    this.rccm,
    this.ifu,
    this.adresse,
    this.ville,
    this.pays,
    this.telephone,
    this.email,
    this.siteWeb,
    this.description,
    required this.statut,
    this.ownerId,
    this.memberCount = 0,
    this.myRole,
  });

  bool get canManage => myRole == 'PROPRIETAIRE' || myRole == 'ADMINISTRATEUR';

  factory Structure.fromJson(Map<String, dynamic> j) => Structure(
        id: j['id'] as String,
        raisonSociale: j['raisonSociale'] as String? ?? '',
        sigle: j['sigle'] as String?,
        typeStructure: j['typeStructure'] as String? ?? 'AUTRE',
        secteurActivite: j['secteurActivite'] as String?,
        rccm: j['rccm'] as String?,
        ifu: j['ifu'] as String?,
        adresse: j['adresse'] as String?,
        ville: j['ville'] as String?,
        pays: j['pays'] as String?,
        telephone: j['telephone'] as String?,
        email: j['email'] as String?,
        siteWeb: j['siteWeb'] as String?,
        description: j['description'] as String?,
        statut: j['statut'] as String? ?? 'EN_ATTENTE',
        ownerId: j['ownerId'] as String?,
        memberCount: j['memberCount'] as int? ?? 0,
        myRole: j['myRole'] as String?,
      );
}

class StructureMember {
  final String id;
  final String userId;
  final String fullName;
  final String email;
  final String roleInterne;
  final String? fonction;
  final bool active;

  StructureMember({
    required this.id,
    required this.userId,
    required this.fullName,
    required this.email,
    required this.roleInterne,
    this.fonction,
    this.active = true,
  });

  factory StructureMember.fromJson(Map<String, dynamic> j) => StructureMember(
        id: j['id'] as String,
        userId: j['userId'] as String,
        fullName: j['fullName'] as String? ?? '',
        email: j['email'] as String? ?? '',
        roleInterne: j['roleInterne'] as String? ?? 'MEMBRE',
        fonction: j['fonction'] as String?,
        active: j['active'] as bool? ?? true,
      );
}

class ParticipantView {
  final String nom;
  final String? prenom;
  final String? email;
  final String? telephone;
  final String? fonction;

  ParticipantView({
    required this.nom,
    this.prenom,
    this.email,
    this.telephone,
    this.fonction,
  });

  factory ParticipantView.fromJson(Map<String, dynamic> j) => ParticipantView(
        nom: j['nom'] as String? ?? '',
        prenom: j['prenom'] as String?,
        email: j['email'] as String?,
        telephone: j['telephone'] as String?,
        fonction: j['fonction'] as String?,
      );
}

class DocumentFile {
  final String id;
  final String nom;
  final String? typeDocument;
  final String url;
  final String? mime;
  final int? taille;

  DocumentFile({
    required this.id,
    required this.nom,
    this.typeDocument,
    required this.url,
    this.mime,
    this.taille,
  });

  factory DocumentFile.fromJson(Map<String, dynamic> j) => DocumentFile(
        id: j['id'] as String,
        nom: j['nom'] as String? ?? 'document',
        typeDocument: j['typeDocument'] as String?,
        url: j['url'] as String? ?? '',
        mime: j['mime'] as String?,
        taille: (j['taille'] as num?)?.toInt(),
      );
}

class Organizer {
  final String id;
  final String nomAffichage;
  final String? description;
  final String? contactEmail;
  final String? contactTelephone;
  final String? siteWeb;
  final String? structureNom;
  final String statut;

  Organizer({
    required this.id,
    required this.nomAffichage,
    this.description,
    this.contactEmail,
    this.contactTelephone,
    this.siteWeb,
    this.structureNom,
    required this.statut,
  });

  factory Organizer.fromJson(Map<String, dynamic> j) => Organizer(
        id: j['id'] as String,
        nomAffichage: j['nomAffichage'] as String? ?? '',
        description: j['description'] as String?,
        contactEmail: j['contactEmail'] as String?,
        contactTelephone: j['contactTelephone'] as String?,
        siteWeb: j['siteWeb'] as String?,
        structureNom: j['structureName'] as String?,
        statut: j['statut'] as String? ?? 'EN_ATTENTE',
      );
}

/// Accreditation roles (CONFERENCIER, EXPOSANT, …) and their French labels.
const kAccreditationRoles = <String, String>{
  'CONFERENCIER': 'Conférencier',
  'EXPOSANT': 'Exposant',
  'MODERATEUR': 'Modérateur',
  'MAITRE_CEREMONIE': 'Maître de cérémonie',
  'PANELISTE': 'Panéliste',
  'COMPETITEUR': 'Compétiteur',
  'INVITE': 'Invité',
  'PRESSE': 'Presse',
  'STAFF': 'Staff / organisation',
  'AUTRE': 'Autre (préciser)',
};

class Accreditation {
  final String id;
  final String eventId;
  final String? activityId;
  final String? activiteNom;
  final String numero;
  final String personneNom;
  final String? personneEmail;
  final String? organisation;
  final String fonction;
  final String fonctionLibelle;
  final String? photoUrl;
  final String statut; // ACTIVE / REVOQUEE
  final String qrImageUrl;
  final String badgePdfUrl;

  Accreditation({
    required this.id,
    required this.eventId,
    this.activityId,
    this.activiteNom,
    required this.numero,
    required this.personneNom,
    this.personneEmail,
    this.organisation,
    required this.fonction,
    required this.fonctionLibelle,
    this.photoUrl,
    required this.statut,
    required this.qrImageUrl,
    required this.badgePdfUrl,
  });

  bool get active => statut == 'ACTIVE';

  factory Accreditation.fromJson(Map<String, dynamic> j) => Accreditation(
        id: j['id'] as String,
        eventId: j['eventId'] as String,
        activityId: j['activityId'] as String?,
        activiteNom: j['activiteNom'] as String?,
        numero: j['numero'] as String? ?? '',
        personneNom: j['personneNom'] as String? ?? '',
        personneEmail: j['personneEmail'] as String?,
        organisation: j['organisation'] as String?,
        fonction: j['fonction'] as String? ?? 'AUTRE',
        fonctionLibelle: j['fonctionLibelle'] as String? ?? '',
        photoUrl: j['photoUrl'] as String?,
        statut: j['statut'] as String? ?? 'ACTIVE',
        qrImageUrl: j['qrImageUrl'] as String? ?? '',
        badgePdfUrl: j['badgePdfUrl'] as String? ?? '',
      );
}

class ScanOutcome {
  final String resultat; // VALIDE / DEJA_UTILISE / INVALIDE
  final String sens; // ENTREE / SORTIE
  final bool reentree;
  final String message;
  final String? eventNom;
  final String? activiteNom;
  final String? participantNom;
  final String? categorieNom;
  final String? numeroBillet;
  final DateTime? premierControleLe;

  ScanOutcome({
    required this.resultat,
    this.sens = 'ENTREE',
    this.reentree = false,
    required this.message,
    this.eventNom,
    this.activiteNom,
    this.participantNom,
    this.categorieNom,
    this.numeroBillet,
    this.premierControleLe,
  });

  bool get sortie => sens == 'SORTIE';

  factory ScanOutcome.fromJson(Map<String, dynamic> j) => ScanOutcome(
        resultat: j['resultat'] as String? ?? 'INVALIDE',
        sens: j['sens'] as String? ?? 'ENTREE',
        reentree: j['reentree'] as bool? ?? false,
        message: j['message'] as String? ?? '',
        eventNom: j['eventNom'] as String?,
        activiteNom: j['activiteNom'] as String?,
        participantNom: j['participantNom'] as String?,
        categorieNom: j['categorieNom'] as String?,
        numeroBillet: j['numeroBillet'] as String?,
        premierControleLe: parseDate(j['premierControleLe']),
      );
}

/// Labels for enum-ish status codes shown in the UI.
String statutLabel(String code) {
  switch (code) {
    case 'EN_ATTENTE':
      return 'En attente';
    case 'VERIFIEE':
      return 'Vérifiée';
    case 'SUSPENDUE':
    case 'SUSPENDU':
      return 'Suspendue';
    case 'APPROUVE':
      return 'Approuvé';
    case 'PAYEE':
    case 'PAYE':
      return 'Payé';
    case 'CONFIRMEE':
    case 'CONFIRME':
      return 'Confirmé';
    case 'ANNULEE':
    case 'ANNULE':
      return 'Annulé';
    case 'REFUSEE':
      return 'Refusé';
    case 'EXPIREE':
    case 'EXPIRE':
      return 'Expiré';
    case 'RESERVE_TEMP':
      return 'Blocage temporaire';
    case 'ATTENTE_PAIEMENT':
      return 'Attente de paiement';
    case 'REUSSI':
      return 'Réussi';
    case 'ECHOUE':
      return 'Échoué';
    case 'REMBOURSE':
      return 'Remboursé';
    case 'EMISE':
      return 'Valide';
    case 'UTILISE':
      return 'Utilisé';
    case 'INSCRIPTIONS_OUVERTES':
      return 'Inscriptions ouvertes';
    case 'INSCRIPTIONS_FERMEES':
      return 'Inscriptions fermées';
    case 'PUBLIE':
      return 'Publié';
    case 'EN_COURS':
      return 'En cours';
    case 'TERMINE':
      return 'Terminé';
    default:
      return code.isEmpty ? '—' : code;
  }
}

String moneyOf(num? amount, [String devise = 'XOF']) => Fmt.money(amount, devise);
