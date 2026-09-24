package app.pbbls.android.testing

import android.content.SharedPreferences

/** Minimal SharedPreferences for booleans — the only type AppearancePreferences writes. */
internal class InMemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ) = values[key] as? Boolean ?: defValue

    override fun edit(): SharedPreferences.Editor =
        object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ) = apply { pending[key!!] = value }

            override fun apply() {
                values.putAll(pending)
            }

            override fun commit(): Boolean = true.also { apply() }

            override fun putString(
                key: String?,
                value: String?,
            ) = this

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ) = this

            override fun putInt(
                key: String?,
                value: Int,
            ) = this

            override fun putLong(
                key: String?,
                value: Long,
            ) = this

            override fun putFloat(
                key: String?,
                value: Float,
            ) = this

            override fun remove(key: String?) = this

            override fun clear() = this
        }

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(
        key: String?,
        defValue: String?,
    ) = defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ) = defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ) = defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ) = defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ) = defValue

    override fun contains(key: String?) = values.containsKey(key)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
