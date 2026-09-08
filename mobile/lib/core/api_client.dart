import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:path_provider/path_provider.dart';
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

  /// Authenticated GET returning raw bytes (QR images, PDF documents).
  Future<List<int>> bytes(String path) async {
    try {
      final res = await dio.get<List<int>>(
        path,
        options: Options(responseType: ResponseType.bytes),
      );
      return res.data ?? const [];
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  /// Downloads an authenticated file to the app cache and returns its local path.
  /// Not supported on Flutter web (no filesystem) — throws [UnsupportedError].
  Future<String> downloadToCache(String path, String filename) async {
    if (kIsWeb) {
      throw UnsupportedError(
          'Le téléchargement de fichiers n\'est pas disponible dans le navigateur.');
    }
    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/$filename');
    final data = await bytes(path);
    await file.writeAsBytes(data, flush: true);
    return file.path;
  }

  /// True when file download / caching is available (native platforms).
  bool get supportsFileDownload => !kIsWeb;

  /// Uploads an image to `POST /api/uploads/image`, returns its public URL.
  Future<String> uploadImage({
    required String filePath,
    required String fileName,
    String folder = 'images',
  }) async {
    try {
      final form = FormData.fromMap({
        'file': await MultipartFile.fromFile(filePath, filename: fileName),
        'dossier': folder,
      });
      final res = await dio.post('/uploads/image', data: form,
          options: Options(contentType: 'multipart/form-data'));
      return (res.data as Map<String, dynamic>)['url'] as String;
    } on DioException catch (e) {
      throw toApiException(e);
    }
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
