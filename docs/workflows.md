# Workflows

## Cycle de vie d'un événement

```
BROUILLON ─► SOUMIS ─► VALIDE ─► PUBLIE ─► INSCRIPTIONS_OUVERTES
   ▲                                              │
   └────────── (retour possible) ◄────────────────┘
                                                  ▼
                              INSCRIPTIONS_FERMEES ─► EN_COURS ─► TERMINE

Actions admin transverses : SUSPENDU, ANNULE (depuis la plupart des états).
```

- **BROUILLON / SOUMIS / VALIDE** : gérés par l'organisateur (soumission) et
  l'admin (validation / refus, permission `EVENT_VALIDATE`).
- **PUBLIE** → visible sur le site public.
- Ouverture/fermeture des inscriptions : manuelle ou automatique selon la période
  d'inscription configurée.

## Workflow d'une inscription (particulier)

```
Choix de l'événement
      ▼
Choix du type de billet + quantité (≤ limite/utilisateur, ≤ quota restant)
      ▼
Formulaire participant(s)
      ▼
Création commande (ticket_order, statut EN_ATTENTE) + réservation du quota
      ▼
Paiement (provider) ──► webhook signé ──► payment REUSSI
      ▼
Inscription validée · billets générés · QR codes émis
      ▼
Email + PDF · Contrôle à l'entrée (scan)
```

Si le paiement échoue ou expire : la commande est annulée et le quota est libéré.

## Workflow d'une réservation de stand (structure)

```
Événement ─► Type de stand ─► Emplacement ─► RESERVE_TEMP (hold 15 min)
      ▼
Informations + documents demandés
      ▼
Paiement avant hold_expires_at
      │  (sinon job @Scheduled ─► EXPIRE ─► stand redevient disponible)
      ▼
payment REUSSI ─► réservation CONFIRME ─► facture + confirmation PDF
```

Statuts d'une réservation : `EN_ATTENTE`, `RESERVE_TEMP`, `ATTENTE_PAIEMENT`,
`PAYE`, `CONFIRME`, `ANNULE`, `EXPIRE`.

## Paiement

Statuts : `EN_ATTENTE`, `REUSSI`, `ECHOUE`, `ANNULE`, `REMBOURSE`.

- Aucune inscription / réservation validée avant `REUSSI`.
- Le webhook `POST /api/payments/webhook` vérifie une signature HMAC et est
  idempotent (déduplication par référence de transaction).
- Interface `PaymentProvider` : implémentation `sandbox` (succès / échec / timeout
  simulés) puis FasoArzeka / mobile money.

## Contrôle à l'entrée

`POST /api/checkins/scan` avec le token du QR code :

| Cas                     | Réponse                                        |
|-------------------------|------------------------------------------------|
| Billet valide           | `VALIDE` + participant, type, événement, heure  |
| Billet déjà scanné      | `DEJA_UTILISE` + date/heure du 1er contrôle     |
| Billet inconnu / annulé | `INVALIDE`                                      |
