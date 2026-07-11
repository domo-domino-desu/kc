package ddd.kc.fake

import ddd.kc.data.security.SecretStore

class TestSecretStore : SecretStore {
  private val values = mutableMapOf<String, String>()

  override fun get(key: String): String? = values[key]

  override fun put(key: String, value: String) {
    values[key] = value
  }

  override fun delete(key: String) {
    values.remove(key)
  }
}
