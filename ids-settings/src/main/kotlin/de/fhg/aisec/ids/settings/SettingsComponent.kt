/*-
 * ========================LICENSE_START=================================
 * ids-settings
 * %%
 * Copyright (C) 2021 Fraunhofer AISEC
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package de.fhg.aisec.ids.settings

import de.fhg.aisec.ids.api.Constants
import de.fhg.aisec.ids.api.infomodel.ConnectorProfile
import de.fhg.aisec.ids.api.settings.ConnectionSettings
import de.fhg.aisec.ids.api.settings.ConnectorConfig
import de.fhg.aisec.ids.api.settings.Settings
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.FileSystems
import java.util.Collections
import java.util.concurrent.ConcurrentMap
import javax.annotation.PreDestroy

@Component
class SettingsComponent : Settings {

    init {
        activate()
    }

    private fun activate() {
        LOG.debug("Open Settings Database {}...", DB_PATH.toFile().absolutePath)

        DB_PATH.toFile().parentFile.mkdirs()

        // Use default reliable (non-mmap) mode and WAL for transaction safety
        mapDB = DBMaker.fileDB(DB_PATH.toFile()).transactionEnable().make()
        var dbVersion = settingsStore.getOrPut(DB_VERSION_KEY) { 1 } as Int
        // Check for unknown DB version
        if (dbVersion > CURRENT_DB_VERSION) {
            LOG.error(
                "Settings database is newer than supported version, data loss er errors are possible!"
            )
        }
        // Migrate old DB versions
        while (dbVersion < CURRENT_DB_VERSION) {
            LOG.info(
                "Migrating settings database from version $dbVersion to version $CURRENT_DB_VERSION..."
            )
            when (dbVersion) {
                1 -> {
                    // Checking ConnectorProfile for errors
                    try {
                        settingsStore[CONNECTOR_PROFILE_KEY]
                    } catch (x: Exception) {
                        // Serialization issue due to infomodel changes, need to rebuild settings
                        // store
                        val tempMap = HashMap<String, Any?>()
                        settingsStore.keys.forEach {
                            if (it != CONNECTOR_PROFILE_KEY) {
                                tempMap[it] = settingsStore[it]
                            }
                        }
                        settingsStore.clear()
                        settingsStore += tempMap
                    }
                    dbVersion = 2
                }
                2 -> {
                    settingsStore -= DAT_KEY
                    dbVersion = 3
                }
            }
            settingsStore[DB_VERSION_KEY] = dbVersion
            mapDB.commit()
            LOG.info("Migration successful")
        }
    }

    @PreDestroy
    fun deactivate() {
        LOG.debug("Close Settings Database...")
        mapDB.close()
    }

    override fun getConnectorConfig() =
        settingsStore.getOrElse(CONNECTOR_SETTINGS_KEY) { ConnectorConfig() } as ConnectorConfig

    override fun setConnectorConfig(connectorConfig: ConnectorConfig) {
        settingsStore[CONNECTOR_SETTINGS_KEY] = connectorConfig
        mapDB.commit()
    }

    override fun getConnectorProfile() =
        settingsStore.getOrElse(CONNECTOR_PROFILE_KEY) { ConnectorProfile() } as ConnectorProfile

    override fun setConnectorProfile(connectorProfile: ConnectorProfile) {
        settingsStore[CONNECTOR_PROFILE_KEY] = connectorProfile
        mapDB.commit()
    }

    override fun getConnectorJsonLd() = settingsStore[CONNECTOR_JSON_LD_KEY] as String?

    override fun setConnectorJsonLd(jsonLd: String?) {
        if (jsonLd == null) {
            settingsStore -= CONNECTOR_JSON_LD_KEY
        } else {
            settingsStore[CONNECTOR_JSON_LD_KEY] = jsonLd
        }
        mapDB.commit()
    }

    override fun getConnectionSettings(connection: String): ConnectionSettings =
        if (connection == Constants.GENERAL_CONFIG) {
            connectionSettings.getOrElse(connection) { ConnectionSettings() }
        } else {
            connectionSettings.getOrPut(connection) {
                getConnectionSettings(Constants.GENERAL_CONFIG)
            }
        }

    override fun setConnectionSettings(connection: String, conSettings: ConnectionSettings) {
        connectionSettings[connection] = conSettings
        mapDB.commit()
    }

    override fun getAllConnectionSettings(): MutableMap<String, ConnectionSettings> =
        Collections.unmodifiableMap(connectionSettings)

    companion object {
        internal const val DB_VERSION_KEY = "db_version"
        internal const val CURRENT_DB_VERSION = 3
        internal const val CONNECTOR_SETTINGS_KEY = "main_config"
        internal const val CONNECTOR_PROFILE_KEY = "connector_profile"
        internal const val CONNECTOR_JSON_LD_KEY = "connector_json_ld"
        internal const val DAT_KEY = "dynamic_attribute_token"
        internal val DB_PATH = FileSystems.getDefault().getPath("etc", "settings.mapdb")
        private val LOG = LoggerFactory.getLogger(SettingsComponent::class.java)
        private lateinit var mapDB: DB
        private val settingsStore: ConcurrentMap<String, Any> by lazy {
            mapDB
                .hashMap("settings_store")
                .keySerializer(Serializer.STRING)
                .valueSerializer(OsgiElsaSerializer<Any>())
                .createOrOpen()
        }
        private val connectionSettings: ConcurrentMap<String, ConnectionSettings> by lazy {
            mapDB
                .hashMap("connection_settings")
                .keySerializer(Serializer.STRING)
                .valueSerializer(OsgiElsaSerializer<ConnectionSettings>())
                .createOrOpen()
        }
    }
}
