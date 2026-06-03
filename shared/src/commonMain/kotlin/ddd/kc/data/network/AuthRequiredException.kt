package ddd.kc.data.network

class AuthRequiredException(message: String = "需要登录后再操作") : RuntimeException(message)
