class UserSummary {
  final String id;
  final String email;
  final String fullName;
  final String type;
  final String status;
  final List<String> roles;
  final List<String> permissions;

  UserSummary({
    required this.id,
    required this.email,
    required this.fullName,
    required this.type,
    required this.status,
    required this.roles,
    required this.permissions,
  });

  factory UserSummary.fromJson(Map<String, dynamic> json) => UserSummary(
        id: json['id'] as String,
        email: json['email'] as String,
        fullName: json['fullName'] as String? ?? '',
        type: json['type'] as String? ?? 'PARTICULIER',
        status: json['status'] as String? ?? 'ACTIF',
        roles: (json['roles'] as List<dynamic>? ?? []).cast<String>(),
        permissions: (json['permissions'] as List<dynamic>? ?? []).cast<String>(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'email': email,
        'fullName': fullName,
        'type': type,
        'status': status,
        'roles': roles,
        'permissions': permissions,
      };
}

class AuthResponse {
  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final UserSummary user;

  AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.user,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        expiresIn: json['expiresIn'] as int? ?? 0,
        user: UserSummary.fromJson(json['user'] as Map<String, dynamic>),
      );
}

class ApiException implements Exception {
  final int status;
  final String code;
  final String message;
  ApiException(this.status, this.code, this.message);

  @override
  String toString() => message;
}
