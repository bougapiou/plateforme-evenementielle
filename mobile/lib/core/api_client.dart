import 'package:dio/dio.dart';
import 'config.dart';
import 'models.dart';
import 'token_store.dart';

/// Thin Dio wrapper: injects the bearer token and refreshes it once on a 401.
class ApiClient {
  final Dio dio;
  final TokenStore _store;
  bool _refreshing = false;

  ApiClient(this._store)
      : dio = Dio(BaseOptions(
          baseUrl: AppConfig.apiBaseUrl,
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 20),
          headers: {'Content-Type': 'application/json'},
        )) {
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final isAuthCall = options.path.contains('/auth/');
        if (!isAuthCall) {
          final token = await _store.accessToken;
          if (token != null) options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (e, handler) async {
        final path = e.requestOptions.path;
        final canRetry = e.response?.statusCode == 401 &&
            !path.contains('/auth/') &&
            !_refreshing;
        if (!canRetry) return handler.next(e);

        final refresh = await _store.refreshToken;
        if (refresh == null) return handler.next(e);

        try {
          _refreshing = true;
          final res = await dio.post('/auth/refresh', data: {'refreshToken': refresh});
          final auth = AuthResponse.fromJson(res.data as Map<String, dynamic>);
          await _store.save(auth);
          _refreshing = false;

          final opts = e.requestOptions;
          opts.headers['Authorization'] = 'Bearer ${auth.accessToken}';
          final retry = await dio.fetch(opts);
          return handler.resolve(retry);
        } catch (_) {
          _refreshing = false;
          await _store.clear();
          return handler.next(e);
        }
      },
    ));
  }

  ApiException toApiException(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      return ApiException(
        e.response?.statusCode ?? 0,
        data['code'] as String? ?? 'ERROR',
        data['message'] as String? ?? 'Une erreur est survenue.',
      );
    }
    return ApiException(0, 'NETWORK', 'Connexion au serveur impossible.');
  }
}
