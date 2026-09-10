import 'package:dio/dio.dart';
import '../core/api_client.dart';
import 'domain.dart';

/// One repository per backend area. Each wraps [ApiClient.dio] and converts
/// Dio failures into [ApiException] so the UI can show a message.
class _Base {
  final ApiClient api;
  _Base(this.api);

  Future<T> _get<T>(String path, T Function(dynamic data) map,
      {Map<String, dynamic>? query}) async {
    try {
      final res = await api.dio.get(path, queryParameters: query);
      return map(res.data);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<T> _post<T>(String path, T Function(dynamic data) map,
      {Object? body}) async {
    try {
      final res = await api.dio.post(path, data: body);
      return map(res.data);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<T> _put<T>(String path, T Function(dynamic data) map,
      {Object? body}) async {
    try {
      final res = await api.dio.put(path, data: body);
      return map(res.data);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<T> _patch<T>(String path, T Function(dynamic data) map,
      {Object? body}) async {
    try {
      final res = await api.dio.patch(path, data: body);
      return map(res.data);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<void> _delete(String path) async {
    try {
      await api.dio.delete(path);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }
}

class UsersRepository extends _Base {
  UsersRepository(super.api);

  Future<Me> me() =>
      _get('/users/me', (d) => Me.fromJson(d as Map<String, dynamic>));

  Future<Me> updateProfile({
    required String firstName,
    required String lastName,
    String? phone,
  }) =>
      _patch(
        '/users/me',
        (d) => Me.fromJson(d as Map<String, dynamic>),
        body: {
          'firstName': firstName,
          'lastName': lastName,
          'phone': phone ?? '',
        },
      );

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    try {
      await api.dio.post('/users/me/password', data: {
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      });
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }
}

class StructuresRepository extends _Base {
  StructuresRepository(super.api);

  Future<List<StructureSummary>> mine() => _get(
        '/structures/mine',
        (d) => (d as List<dynamic>)
            .map((e) => StructureSummary.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<Structure> get(String id) => _get(
        '/structures/$id',
        (d) => Structure.fromJson(d as Map<String, dynamic>),
      );

  Future<Structure> create(Map<String, dynamic> body) => _post(
        '/structures',
        (d) => Structure.fromJson(d as Map<String, dynamic>),
        body: body,
      );

  Future<Structure> update(String id, Map<String, dynamic> body) => _put(
        '/structures/$id',
        (d) => Structure.fromJson(d as Map<String, dynamic>),
        body: body,
      );

  Future<List<StructureMember>> members(String id) => _get(
        '/structures/$id/members',
        (d) => (d as List<dynamic>)
            .map((e) => StructureMember.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<StructureMember> addMember(
    String id, {
    required String email,
    required String roleInterne,
    String? fonction,
  }) =>
      _post(
        '/structures/$id/members',
        (d) => StructureMember.fromJson(d as Map<String, dynamic>),
        body: {
          'email': email,
          'roleInterne': roleInterne,
          if (fonction != null) 'fonction': fonction,
        },
      );

  Future<void> removeMember(String id, String userId) =>
      _delete('/structures/$id/members/$userId');
}

class OrganizersRepository extends _Base {
  OrganizersRepository(super.api);

  Future<Organizer?> me() async {
    try {
      final res = await api.dio.get('/organizers/me');
      return Organizer.fromJson(res.data as Map<String, dynamic>);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      throw api.toApiException(e);
    }
  }

  Future<Organizer> apply({
    required String nomAffichage,
    String? description,
    String? structureId,
    String? contactEmail,
    String? contactTelephone,
    String? siteWeb,
  }) =>
      _post(
        '/organizers/apply',
        (d) => Organizer.fromJson(d as Map<String, dynamic>),
        body: {
          'nomAffichage': nomAffichage,
          if (description != null) 'description': description,
          if (structureId != null) 'structureId': structureId,
          if (contactEmail != null) 'contactEmail': contactEmail,
          if (contactTelephone != null) 'contactTelephone': contactTelephone,
          if (siteWeb != null) 'siteWeb': siteWeb,
        },
      );
}

class EventsRepository extends _Base {
  EventsRepository(super.api);

  Future<Paged<EventSummary>> search({
    String? q,
    String? categorie,
    String? ville,
    int page = 0,
  }) =>
      _get(
        '/public/events',
        (d) => Paged.fromJson(d as Map<String, dynamic>, EventSummary.fromJson),
        query: {
          if (q != null && q.isNotEmpty) 'search': q,
          if (categorie != null && categorie.isNotEmpty) 'categorie': categorie,
          if (ville != null && ville.isNotEmpty) 'ville': ville,
          'page': page,
          'size': 12,
        },
      );

  Future<List<EventCategory>> categories() => _get(
        '/public/event-categories',
        (d) => (d as List<dynamic>)
            .map((e) => EventCategory.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<EventDetail> bySlug(String slug) => _get(
        '/public/events/$slug',
        (d) => EventDetail.fromJson(d as Map<String, dynamic>),
      );

  Future<List<EventTicketType>> tickets(String slug) => _get(
        '/public/events/$slug/tickets',
        (d) => (d as List<dynamic>)
            .map((e) => EventTicketType.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<List<StandType>> standTypes(String slug) => _get(
        '/public/events/$slug/stand-types',
        (d) => (d as List<dynamic>)
            .map((e) => StandType.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<List<Stand>> stands(String slug) => _get(
        '/public/events/$slug/stands',
        (d) => (d as List<dynamic>)
            .map((e) => Stand.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class TicketsRepository extends _Base {
  TicketsRepository(super.api);

  Future<TicketOrder> createOrder({
    required String eventId,
    required Map<String, int> lignes, // eventTicketId -> quantite
    String? structureId,
  }) =>
      _post(
        '/ticket-orders',
        (d) => TicketOrder.fromJson(d as Map<String, dynamic>),
        body: {
          'eventId': eventId,
          if (structureId != null) 'structureId': structureId,
          'lignes': lignes.entries
              .map((e) => {'eventTicketId': e.key, 'quantite': e.value})
              .toList(),
        },
      );

  Future<TicketOrder> order(String id) => _get(
        '/ticket-orders/$id',
        (d) => TicketOrder.fromJson(d as Map<String, dynamic>),
      );

  /// Participate in a free activity: issues one free electronic ticket now.
  Future<Ticket> attendActivity(String activityId) => _post(
        '/activities/$activityId/attend',
        (d) => Ticket.fromJson(d as Map<String, dynamic>),
      );

  Future<Paged<TicketOrder>> myOrders({int page = 0}) => _get(
        '/ticket-orders/my',
        (d) => Paged.fromJson(d as Map<String, dynamic>, TicketOrder.fromJson),
        query: {'page': page, 'size': 20},
      );

  Future<TicketOrder> cancelOrder(String id) => _post(
        '/ticket-orders/$id/cancel',
        (d) => TicketOrder.fromJson(d as Map<String, dynamic>),
      );

  Future<List<Ticket>> myTickets() => _get(
        '/tickets/my',
        (d) => (d as List<dynamic>)
            .map((e) => Ticket.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<Ticket> ticket(String id) =>
      _get('/tickets/$id', (d) => Ticket.fromJson(d as Map<String, dynamic>));
}

class RegistrationsRepository extends _Base {
  RegistrationsRepository(super.api);

  Future<Registration> register({
    required String eventId,
    required String type, // PARTICULIER / STRUCTURE
    String? structureId,
    String? contactNom,
    String? contactEmail,
    String? contactTelephone,
    String? informations,
    List<Map<String, dynamic>> participants = const [],
    Map<String, int> tickets = const {},
  }) =>
      _post(
        '/events/$eventId/registrations',
        (d) => Registration.fromJson(d as Map<String, dynamic>),
        body: {
          'type': type,
          if (structureId != null) 'structureId': structureId,
          if (contactNom != null) 'contactNom': contactNom,
          if (contactEmail != null) 'contactEmail': contactEmail,
          if (contactTelephone != null) 'contactTelephone': contactTelephone,
          if (informations != null) 'informations': informations,
          if (participants.isNotEmpty) 'participants': participants,
          if (tickets.isNotEmpty)
            'tickets': tickets.entries
                .map((e) => {'eventTicketId': e.key, 'quantite': e.value})
                .toList(),
        },
      );

  Future<Paged<Registration>> mine({int page = 0}) => _get(
        '/registrations/my',
        (d) => Paged.fromJson(d as Map<String, dynamic>, Registration.fromJson),
        query: {'page': page, 'size': 20},
      );

  Future<Registration> get(String id) => _get(
        '/registrations/$id',
        (d) => Registration.fromJson(d as Map<String, dynamic>),
      );

  Future<Registration> cancel(String id) => _post(
        '/registrations/$id/cancel',
        (d) => Registration.fromJson(d as Map<String, dynamic>),
      );

  Future<List<DocumentFile>> documents(String id) => _get(
        '/registrations/$id/documents',
        (d) => (d as List<dynamic>)
            .map((e) => DocumentFile.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<DocumentFile> uploadDocument(
    String id, {
    required String filePath,
    required String fileName,
    String? type,
  }) async {
    try {
      final form = FormData.fromMap({
        'file': await MultipartFile.fromFile(filePath, filename: fileName),
        if (type != null) 'type': type,
      });
      final res = await api.dio.post(
        '/registrations/$id/documents',
        data: form,
        options: Options(contentType: 'multipart/form-data'),
      );
      return DocumentFile.fromJson(res.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<void> deleteDocument(String registrationId, String documentId) =>
      _delete('/registrations/$registrationId/documents/$documentId');
}

class StandsRepository extends _Base {
  StandsRepository(super.api);

  Future<StandReservation> reserve({
    required String eventId,
    required String standId,
    String? structureId,
    String? informations,
  }) =>
      _post(
        '/stand-reservations',
        (d) => StandReservation.fromJson(d as Map<String, dynamic>),
        body: {
          'eventId': eventId,
          'standId': standId,
          if (structureId != null) 'structureId': structureId,
          if (informations != null) 'informations': informations,
        },
      );

  Future<Paged<StandReservation>> mine({int page = 0}) => _get(
        '/stand-reservations/my',
        (d) =>
            Paged.fromJson(d as Map<String, dynamic>, StandReservation.fromJson),
        query: {'page': page, 'size': 20},
      );

  Future<StandReservation> get(String id) => _get(
        '/stand-reservations/$id',
        (d) => StandReservation.fromJson(d as Map<String, dynamic>),
      );

  Future<StandReservation> cancel(String id) => _post(
        '/stand-reservations/$id/cancel',
        (d) => StandReservation.fromJson(d as Map<String, dynamic>),
      );
}

class PaymentsRepository extends _Base {
  PaymentsRepository(super.api);

  /// Initiates a payment for a ticket order or a stand reservation.
  Future<Payment> initiate({
    required String targetType, // TICKET_ORDER / STAND_RESERVATION
    required String targetId,
    String? moyen,
  }) =>
      _post(
        '/payments',
        (d) => Payment.fromJson(d as Map<String, dynamic>),
        body: {
          'targetType': targetType,
          'targetId': targetId,
          if (moyen != null) 'moyen': moyen,
        },
      );

  /// Sandbox only: simulates the provider callback for a payment reference.
  Future<Payment> simulate(String reference, {String outcome = 'SUCCESS'}) async {
    try {
      final res = await api.dio.post(
        '/payments/$reference/simulate',
        queryParameters: {'outcome': outcome},
      );
      return Payment.fromJson(res.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<Payment> get(String id) =>
      _get('/payments/$id', (d) => Payment.fromJson(d as Map<String, dynamic>));

  Future<Paged<Payment>> mine({int page = 0}) => _get(
        '/payments/my',
        (d) => Paged.fromJson(d as Map<String, dynamic>, Payment.fromJson),
        query: {'page': page, 'size': 20},
      );
}

class InvoicesRepository extends _Base {
  InvoicesRepository(super.api);

  Future<List<Invoice>> mine() => _get(
        '/invoices/my',
        (d) => (d as List<dynamic>)
            .map((e) => Invoice.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class NotificationsRepository extends _Base {
  NotificationsRepository(super.api);

  Future<Paged<AppNotification>> mine({int page = 0}) => _get(
        '/notifications',
        (d) =>
            Paged.fromJson(d as Map<String, dynamic>, AppNotification.fromJson),
        query: {'page': page, 'size': 20},
      );

  Future<int> unreadCount() => _get(
        '/notifications/unread-count',
        (d) => ((d as Map<String, dynamic>)['count'] as num?)?.toInt() ?? 0,
      );

  Future<void> markRead(String id) async {
    try {
      await api.dio.post('/notifications/$id/read');
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<void> markAllRead() async {
    try {
      await api.dio.post('/notifications/read-all');
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }
}

class CheckinRepository extends _Base {
  CheckinRepository(super.api);

  Future<ScanOutcome> scan({
    required String token,
    required String eventId,
    String? activityId,
  }) =>
      _post(
        '/checkins/scan',
        (d) => ScanOutcome.fromJson(d as Map<String, dynamic>),
        body: {
          'token': token,
          'eventId': eventId,
          if (activityId != null) 'activityId': activityId,
        },
      );

  /// Events for which the current user may run entry control (organiser, control
  /// staff, or admin) — only events in a scannable state are returned.
  Future<List<EventSummary>> myControllableEvents() => _get(
        '/checkins/events',
        (d) => (d as List<dynamic>)
            .map((e) => EventSummary.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  /// Activities of an event, to pick which one to control.
  Future<List<Activity>> eventActivities(String eventId) => _get(
        '/checkins/events/$eventId/activities',
        (d) => (d as List<dynamic>)
            .map((e) => Activity.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
