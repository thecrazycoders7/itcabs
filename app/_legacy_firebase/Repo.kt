package com.itcabs

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** All Firestore access. One instance for the app (see ItCabsApp). */
class Repo(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    val uid: String? get() = auth.currentUser?.uid

    // --- profile ---

    suspend fun saveProfile(p: UserProfile) {
        val id = uid ?: error("not signed in")
        db.collection("users").document(id).set(p.copy(uid = id)).await()
    }

    /** Live profile for the signed-in user (null until it exists). */
    fun myProfile(): Flow<UserProfile?> = callbackFlow {
        val id = uid
        if (id == null) { trySend(null); awaitClose { }; return@callbackFlow }
        val reg = db.collection("users").document(id)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(UserProfile::class.java))
            }
        awaitClose { reg.remove() }
    }

    suspend fun uploadPhoto(bytes: ByteArray): String {
        val id = uid ?: error("not signed in")
        val ref = storage.reference.child("driver_photos/$id.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    // --- posting (coordinator) ---

    /** Create a job and its legs. Legs start OPEN. */
    suspend fun postJob(job: Job, legs: List<Leg>) {
        val id = uid ?: error("not signed in")
        val jobRef = db.collection("jobs").document()
        val batch = db.batch()
        val fullJob = job.copy(id = jobRef.id, coordinatorId = id, createdAt = now())
        batch.set(jobRef, fullJob)
        legs.forEach { leg ->
            val legRef = jobRef.collection("legs").document()
            batch.set(legRef, leg.copy(
                id = legRef.id, jobId = jobRef.id, coordinatorId = id,
                office = job.office, shift = job.shift, status = LegStatus.OPEN.name,
            ))
        }
        batch.commit().await()
    }

    /** All legs the signed-in coordinator posted, live, for the dashboard. */
    fun myLegs(): Flow<List<Leg>> = legsQuery(
        db.collectionGroup("legs").whereEqualTo("coordinatorId", uid.orEmpty())
    )

    // --- browsing + claiming (driver) ---

    /** Open legs across all jobs, live. area/vehicleType are optional filters. */
    fun openLegs(area: String? = null, vehicleType: String? = null): Flow<List<Leg>> {
        var q: Query = db.collectionGroup("legs").whereEqualTo("status", LegStatus.OPEN.name)
        if (!area.isNullOrBlank()) q = q.whereEqualTo("area", area)
        if (!vehicleType.isNullOrBlank()) q = q.whereEqualTo("vehicleType", vehicleType)
        return legsQuery(q)
    }

    /** Legs this driver has claimed, live, for their "my trips" view. */
    fun myClaims(): Flow<List<Leg>> = legsQuery(
        db.collectionGroup("legs").whereEqualTo("claimedBy", uid.orEmpty())
    )

    /**
     * First-claim-wins. Runs in a Firestore transaction so exactly one driver
     * can move a leg OPEN -> CLAIMED; a second concurrent claim reads status !=
     * OPEN and throws. This replaces the Telegram "someone reply 'closed'" mess.
     * Requires a verified, unblocked driver (also enforced in security rules).
     */
    suspend fun claimLeg(legPath: String, driverName: String) {
        val id = uid ?: error("not signed in")
        val legRef = db.document(legPath)
        db.runTransaction { tx ->
            val snap = tx.get(legRef)
            if (!canClaim(snap.getString("status"))) throw IllegalStateException("Leg already taken")
            tx.update(legRef, mapOf(
                "status" to LegStatus.CLAIMED.name,
                "claimedBy" to id,
                "claimedByName" to driverName,
                "claimedAt" to now(),
            ))
        }.await()
    }

    /** Coordinator advances a leg through the workflow. */
    suspend fun setLegStatus(legPath: String, status: LegStatus) {
        db.document(legPath).update("status", status.name).await()
    }

    /** Coordinator rates the driver after completion; updates aggregate on the driver. */
    suspend fun rateDriver(legPath: String, driverUid: String, stars: Int) {
        val legRef = db.document(legPath)
        val driverRef = db.collection("users").document(driverUid)
        db.runTransaction { tx ->
            val d = tx.get(driverRef)
            val sum = (d.getDouble("ratingSum") ?: 0.0) + stars
            val count = (d.getLong("ratingCount") ?: 0) + 1
            tx.update(driverRef, mapOf("ratingSum" to sum, "ratingCount" to count))
            tx.update(legRef, "rated", true)
        }.await()
    }

    // --- helpers ---

    private fun legsQuery(q: Query): Flow<List<Leg>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.toObject(Leg::class.java) }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        fun newRequestId() = UUID.randomUUID().toString()
        /** A leg is claimable iff it is currently OPEN. The one rule that makes claiming first-wins. */
        fun canClaim(currentStatus: String?) = currentStatus == LegStatus.OPEN.name
    }
}
