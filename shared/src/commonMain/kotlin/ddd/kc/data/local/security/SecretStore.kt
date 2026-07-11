package ddd.kc.data.local.security

import eu.anifantakis.lib.ksafe.KSafe

interface SecretStore {
  fun get(key: String): String?

  fun put(key: String, value: String)

  fun delete(key: String)
}

class KSafeSecretStore(private val vault: KSafe) : SecretStore {
  override fun get(key: String): String? = vault.getDirect(key, "").takeIf(String::isNotBlank)

  override fun put(key: String, value: String) {
    vault.putDirect(key, value)
  }

  override fun delete(key: String) {
    vault.deleteDirect(key)
  }
}
