package dev.vereda.app

import android.content.Context
import dev.vereda.app.data.FileIdentityStore
import dev.vereda.app.data.Profile
import dev.vereda.app.data.VeredaDatabase
import dev.vereda.app.mesh.MeshRepository
import dev.vereda.core.Identity
import java.io.File

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val profile = Profile(appContext)
    val identity: Identity = FileIdentityStore(File(appContext.filesDir, "identity.bin")).loadOrCreate()
    val db: VeredaDatabase = VeredaDatabase.create(appContext)
    val repository = MeshRepository(appContext, identity, profile, db)
}
