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
  }) =>
      _post(
        '/ticket-orders',
        (d) => TicketOrder.fromJson(d as Map<String, dynamic>),
        body: {
          'eventId': eventId,
          'lignes': lignes.entries
              .map((e) => {'eventTicketId': e.key, 'quantite': e.value})
              .toList(),
        },
      );

  Future<TicketOrder> order(String id) => _get(
        '/ticket-orders/$id',
        (d) => TicketOrder.fromJson(d as Map<String, dynamic>),
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
}

class StandsRepository extends _Base {
  StandsRepository(super.api);

  Future<StandReservation> reserve({
    required String eventId,
    required String standId,
    String? informations,
  }) =>
      _post(
        '/stand-reservations',
        (d) => StandReservation.fromJson(d as Map<String, dynamic>),
        body: {
          'eventId': eventId,
          'standId': standId,
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

  Future<ScanOutcome> scan({required String token, required String eventId}) =>
      _post(
        '/checkins/scan',
        (d) => ScanOutcome.fromJson(d as Map<String, dynamic>),
        body: {'token': token, 'eventId': eventId},
      );

  /// Events for which the current user may run entry control (organiser, control
  /// staff, or admin) — only events in a scannable state are returned.
  Future<List<EventSummary>> myControllableEvents() => _get(
        '/checkins/events',
        (d) => (d as List<dynamic>)
            .map((e) => EventSummary.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
