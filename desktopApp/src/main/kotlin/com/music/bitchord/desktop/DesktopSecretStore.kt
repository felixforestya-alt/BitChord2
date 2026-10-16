package com.music.bitchord.desktop

import com.music.bitchord.desktop.secrets.SecretServiceApi
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.Variant

/** The platform's own password store, for the credentials this app holds. */
internal object DesktopSecretStore {

    /** Plain-text transport. */
    private const val PLAIN = "plain"

    private const val DEFAULT_COLLECTION = "/org/freedesktop/secrets/aliases/default"

    /**
     * Runs [block] with an open Secret Service session, or returns null.
     *
     * Secret Service is a Linux session-bus API. Elsewhere there is no bus to reach, and the failed
     * connect takes about ten seconds on macOS, which every account switch and sign-out paid.
     */
    private fun <T> withService(
        block: (DBusConnection, SecretServiceApi.Service, DBusPath) -> T?,
    ): T? = if (!DesktopPlatform.isLinux) null else runCatching {
        DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
            val service = connection.getRemoteObject(
                SecretServiceApi.BUS_NAME,
                SecretServiceApi.SERVICE_PATH,
                SecretServiceApi.Service::class.java,
            )
            val session = service.OpenSession(PLAIN, Variant("")).session
            block(connection, service, session)
        }
    }.getOrElse {
        DesktopTrackLog.log("secret store unavailable: ${it.message}")
        null
    }

    // ---- Windows: the current user's DPAPI ------------------------------------
    //
    // Windows has no Secret Service, and the credential used to fall through to a plain registry
    // value anyone with the user's registry could read. DPAPI seals it to this Windows account
    // instead; what lands in the registry is ciphertext only this user can open.

    private fun windowsKey(attributes: Map<String, String>): String {
        val canonical = attributes.toSortedMap().entries.joinToString("\u0000") { "${it.key}=${it.value}" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "secret_dpapi_${digest.take(32)}"
    }

    private fun windowsLookup(attributes: Map<String, String>): ByteArray? {
        val sealed = DesktopPreferenceChunks.read(DesktopPersistence().preferences, windowsKey(attributes))
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val bytes = runCatching { java.util.Base64.getDecoder().decode(sealed) }.getOrNull() ?: return null
        return DesktopWindowsCrypto.unprotect(bytes)
    }

    private fun windowsStore(attributes: Map<String, String>, secret: ByteArray): Boolean {
        val sealed = DesktopWindowsCrypto.protect(secret) ?: return false
        DesktopPreferenceChunks.write(
            DesktopPersistence().preferences,
            windowsKey(attributes),
            java.util.Base64.getEncoder().encodeToString(sealed),
        )
        return true
    }

    private fun windowsRemove(attributes: Map<String, String>): Boolean {
        DesktopPreferenceChunks.remove(DesktopPersistence().preferences, windowsKey(attributes))
        return true
    }

    /** The first secret stored under [attributes], as bytes. */
    fun lookup(attributes: Map<String, String>): ByteArray? =
        if (DesktopPlatform.isWindows) windowsLookup(attributes) else linuxLookup(attributes)

    /** Stores [secret] under [attributes], replacing whatever was there. */
    fun store(label: String, attributes: Map<String, String>, secret: ByteArray): Boolean =
        if (DesktopPlatform.isWindows) windowsStore(attributes, secret) else linuxStore(label, attributes, secret)

    /** Forgets everything stored under [attributes]. */
    fun remove(attributes: Map<String, String>): Boolean =
        if (DesktopPlatform.isWindows) windowsRemove(attributes) else linuxRemove(attributes)

    /** Whether a store is actually reachable, for the settings screen to say so. */
    fun isAvailable(): Boolean =
        if (DesktopPlatform.isWindows) {
            DesktopWindowsCrypto.protect(byteArrayOf(1)) != null
        } else {
            withService { _, _, _ -> true } == true
        }

    // ---- Linux: Secret Service -------------------------------------------------

    private fun linuxLookup(attributes: Map<String, String>): ByteArray? = withService { _, service, session ->
        val found = service.SearchItems(attributes)
        val items = buildList {
            addAll(found.unlocked)
            if (found.locked.isNotEmpty()) {
                runCatching { service.Unlock(found.locked) }.getOrNull()?.let { addAll(it.unlocked) }
            }
        }
        if (items.isEmpty()) return@withService null
        service.GetSecrets(items, session).values.firstOrNull()?.value
    }

    private fun linuxStore(label: String, attributes: Map<String, String>, secret: ByteArray): Boolean =
        withService { connection, service, session ->
            val collection = runCatching { service.ReadAlias("default")?.path }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != "/" }
                ?: DEFAULT_COLLECTION
            val properties = mapOf<String, Variant<*>>(
                "org.freedesktop.Secret.Item.Label" to Variant(label),
                "org.freedesktop.Secret.Item.Attributes" to Variant(attributes, "a{ss}"),
            )
            val item = connection.getRemoteObject(
                SecretServiceApi.BUS_NAME,
                collection,
                SecretServiceApi.Collection::class.java,
            )
            item.CreateItem(
                properties,
                SecretServiceApi.Secret(session, ByteArray(0), secret, "text/plain; charset=utf8"),
                true,
            )
            true
        } ?: false

    private fun linuxRemove(attributes: Map<String, String>): Boolean = withService { connection, service, _ ->
        val found = service.SearchItems(attributes)
        (found.unlocked + found.locked).forEach { path ->
            runCatching {
                connection.getRemoteObject(
                    SecretServiceApi.BUS_NAME,
                    path.path,
                    SecretServiceApi.Item::class.java,
                ).Delete()
            }
        }
        true
    } ?: false
}
