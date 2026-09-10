import 'package:dio/dio.dart';
import '../core/api_client.dart';
import 'domain.dart';

/// Organiser-side CRUD for events and their sub-resources (programme, billetterie,
/// stands, intervenants, partenaires) + workflow.
class OrganizerEventsRepository {
  final ApiClient api;
  OrganizerEventsRepository(this.api);

  Future<T> _req<T>(Future<Response<dynamic>> Function() call,
      T Function(dynamic) map) async {
    try {
      final res = await call();
      return map(res.data);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  // --- events ---

  Future<Paged<EventSummary>> mine({int page = 0, String? statut}) => _req(
        () => api.dio.get('/events/mine', queryParameters: {
          'page': page,
          'size': 30,
          if (statut != null) 'statut': statut,
        }),
        (d) => Paged.fromJson(d as Map<String, dynamic>, EventSummary.fromJson),
      );

  Future<EventFull> get(String id) => _req(
        () => api.dio.get('/events/$id'),
        (d) => EventFull.fromJson(d as Map<String, dynamic>),
      );

  Future<EventFull> create(Map<String, dynamic> body) => _req(
        () => api.dio.post('/events', data: body),
        (d) => EventFull.fromJson(d as Map<String, dynamic>),
      );

  Future<EventFull> update(String id, Map<String, dynamic> body) => _req(
        () => api.dio.put('/events/$id', data: body),
        (d) => EventFull.fromJson(d as Map<String, dynamic>),
      );

  Future<void> delete(String id) => _req(() => api.dio.delete('/events/$id'), (_) {});

  Future<EventFull> submit(String id) => _req(
        () => api.dio.post('/events/$id/submit'),
        (d) => EventFull.fromJson(d as Map<String, dynamic>),
      );

  Future<EventFull> publish(String id) => _req(
        () => api.dio.post('/events/$id/publish'),
        (d) => EventFull.fromJson(d as Map<String, dynamic>),
      );

  Future<EventFull> openRegistrations(String id) => _req(
        () => api.dio.post('/events/$id/open-registrations'),
        (d) => EventFull.fromJson(d as Map<String, dynamic>),
      );

  Future<EventFull> closeRegistrations(String id) => _req(
        () => api.dio.post('/events/$id/close-registrations'),
        (d) => EventFull.fromJson(d as Map<String, dynamic>),
      );

  // --- programme / activités ---

  Future<List<Activity>> activities(String eventId) => _req(
        () => api.dio.get('/events/$eventId/activities'),
        (d) => (d as List).map((e) => Activity.fromJson(e as Map<String, dynamic>)).toList(),
      );

  Future<void> saveActivity(String eventId, Map<String, dynamic> body, {String? id}) => _req(
        () => id == null
            ? api.dio.post('/events/$eventId/activities', data: body)
            : api.dio.put('/events/$eventId/activities/$id', data: body),
        (_) {},
      );

  Future<void> deleteActivity(String eventId, String id) =>
      _req(() => api.dio.delete('/events/$eventId/activities/$id'), (_) {});

  // --- intervenants ---

  Future<List<Speaker>> speakers(String eventId) => _req(
        () => api.dio.get('/events/$eventId/speakers'),
        (d) => (d as List).map((e) => Speaker.fromJson(e as Map<String, dynamic>)).toList(),
      );

  Future<void> saveSpeaker(String eventId, Map<String, dynamic> body, {String? id}) => _req(
        () => id == null
            ? api.dio.post('/events/$eventId/speakers', data: body)
            : api.dio.put('/events/$eventId/speakers/$id', data: body),
        (_) {},
      );

  Future<void> deleteSpeaker(String eventId, String id) =>
      _req(() => api.dio.delete('/events/$eventId/speakers/$id'), (_) {});

  // --- partenaires ---

  Future<List<Partner>> partners(String eventId) => _req(
        () => api.dio.get('/events/$eventId/partners'),
        (d) => (d as List).map((e) => Partner.fromJson(e as Map<String, dynamic>)).toList(),
      );

  Future<void> savePartner(String eventId, Map<String, dynamic> body, {String? id}) => _req(
        () => id == null
            ? api.dio.post('/events/$eventId/partners', data: body)
            : api.dio.put('/events/$eventId/partners/$id', data: body),
        (_) {},
      );

  Future<void> deletePartner(String eventId, String id) =>
      _req(() => api.dio.delete('/events/$eventId/partners/$id'), (_) {});

  // --- billetterie ---

  Future<List<EventTicketType>> tickets(String eventId) => _req(
        () => api.dio.get('/events/$eventId/tickets'),
        (d) => (d as List)
            .map((e) => EventTicketType.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<void> saveTicket(String eventId, Map<String, dynamic> body, {String? id}) => _req(
        () => id == null
            ? api.dio.post('/events/$eventId/tickets', data: body)
            : api.dio.put('/events/$eventId/tickets/$id', data: body),
        (_) {},
      );

  Future<void> deleteTicket(String eventId, String id) =>
      _req(() => api.dio.delete('/events/$eventId/tickets/$id'), (_) {});

  // --- stands ---

  Future<List<StandType>> standTypes(String eventId) => _req(
        () => api.dio.get('/events/$eventId/stand-types'),
        (d) => (d as List)
            .map((e) => StandType.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<void> saveStandType(String eventId, Map<String, dynamic> body, {String? id}) => _req(
        () => id == null
            ? api.dio.post('/events/$eventId/stand-types', data: body)
            : api.dio.put('/events/$eventId/stand-types/$id', data: body),
        (_) {},
      );

  Future<void> deleteStandType(String eventId, String id) =>
      _req(() => api.dio.delete('/events/$eventId/stand-types/$id'), (_) {});

  // --- accréditations / badges ---

  Future<List<Accreditation>> accreditations(String eventId) => _req(
        () => api.dio.get('/events/$eventId/accreditations'),
        (d) => (d as List<dynamic>)
            .map((e) => Accreditation.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  Future<Accreditation> issueAccreditation(
          String eventId, Map<String, dynamic> body) =>
      _req(
        () => api.dio.post('/events/$eventId/accreditations', data: body),
        (d) => Accreditation.fromJson(d as Map<String, dynamic>),
      );

  Future<Accreditation> revokeAccreditation(String id) => _req(
        () => api.dio.post('/accreditations/$id/revoke'),
        (d) => Accreditation.fromJson(d as Map<String, dynamic>),
      );
}
