package ddd.kc.data.remote.network

class AuthRequiredException : RuntimeException("Authentication required")

class InvalidCredentialsException : RuntimeException("Username or password is incorrect")
