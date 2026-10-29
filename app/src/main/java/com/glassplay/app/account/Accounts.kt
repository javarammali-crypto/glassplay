package com.glassplay.app.account

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.glassplay.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class UserProfile(val uid: String, val email: String, val firstName: String, val lastName: String) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val initials: String
        get() = listOf(firstName, lastName).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString("")
            .ifEmpty { email.firstOrNull()?.uppercase() ?: "?" }
}

/** A sign-in problem worded for the viewer. */
class AccountException(message: String) : Exception(message)

/**
 * Firebase accounts: email and password, or Google. The Firebase project is identified by
 * the values in firebase.properties; without them (development builds) accounts are off.
 */
class AccountRepository(private val context: Context) {
    val enabled: Boolean = BuildConfig.FIREBASE_API_KEY.isNotEmpty() &&
        BuildConfig.FIREBASE_APP_ID.isNotEmpty() &&
        BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty()

    private val auth: FirebaseAuth? by lazy {
        if (!enabled) return@lazy null
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }
        FirebaseAuth.getInstance()
    }
    val db: FirebaseFirestore? by lazy { auth?.let { FirebaseFirestore.getInstance() } }

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    /** The signed-in account, or null. */
    val user: StateFlow<FirebaseUser?> = _user

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    /** False until Firebase has said whether someone is signed in. */
    private val _ready = MutableStateFlow(!enabled)
    val ready: StateFlow<Boolean> = _ready

    private var profileListener: ListenerRegistration? = null

    fun start() {
        val a = auth ?: return
        a.addAuthStateListener { fa ->
            val u = fa.currentUser
            _user.value = u
            _ready.value = true
            watchProfile(u)
        }
    }

    private fun watchProfile(u: FirebaseUser?) {
        profileListener?.remove()
        profileListener = null
        if (u == null) {
            _profile.value = null
            return
        }
        // Until the profile document loads, fall back to what the account itself knows.
        _profile.value = _profile.value?.takeIf { it.uid == u.uid } ?: fromAccount(u)
        profileListener = db?.collection("users")?.document(u.uid)?.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                _profile.value = UserProfile(
                    uid = u.uid,
                    email = snap.getString("email") ?: u.email.orEmpty(),
                    firstName = snap.getString("firstName").orEmpty(),
                    lastName = snap.getString("lastName").orEmpty(),
                )
            }
        }
    }

    private fun fromAccount(u: FirebaseUser): UserProfile {
        val parts = u.displayName.orEmpty().trim().split(Regex("\\s+"), limit = 2)
        return UserProfile(u.uid, u.email.orEmpty(), parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
    }

    suspend fun signIn(email: String, password: String) = friendly {
        requireAuth().signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(firstName: String, lastName: String, email: String, password: String) = friendly {
        val result = requireAuth().createUserWithEmailAndPassword(email.trim(), password).await()
        val u = result.user ?: throw AccountException("Couldn't create the account. Try again.")
        saveProfile(u.uid, u.email.orEmpty(), firstName.trim(), lastName.trim())
    }

    /** Shows Google's account picker and signs in with the chosen account. */
    suspend fun signInWithGoogle(activity: Activity) = friendly {
        val a = requireAuth()
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) throw AccountException("Google sign-in isn't set up yet.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AccountException("Google didn't return an account. Try again.")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val result = a.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        val u = result.user ?: return@friendly
        // First Google sign-in: start the profile with the names Google knows.
        val existing = db?.collection("users")?.document(u.uid)?.get()?.await()
        if (existing == null || !existing.exists()) {
            saveProfile(u.uid, u.email.orEmpty(), google.givenName.orEmpty(), google.familyName.orEmpty())
        }
    }

    suspend fun resetPassword(email: String) = friendly {
        requireAuth().sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun updateName(firstName: String, lastName: String) = friendly {
        val u = _user.value ?: throw AccountException("You're signed out.")
        saveProfile(u.uid, u.email.orEmpty(), firstName.trim(), lastName.trim())
    }

    suspend fun signOut() {
        auth?.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to clear.
        }
    }

    private suspend fun saveProfile(uid: String, email: String, firstName: String, lastName: String) {
        _profile.value = UserProfile(uid, email, firstName, lastName)
        db?.collection("users")?.document(uid)
            ?.set(mapOf("email" to email, "firstName" to firstName, "lastName" to lastName), SetOptions.merge())
            ?.await()
    }

    private fun requireAuth(): FirebaseAuth = auth ?: throw AccountException("Accounts aren't set up in this build.")

    /** Runs a Firebase call and turns its errors into messages people understand. */
    private suspend fun <T> friendly(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AccountException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        throw AccountException("")
    } catch (e: NoCredentialException) {
        throw AccountException("No Google account was found on this phone.")
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw AccountException("Choose a stronger password: at least 6 characters.")
    } catch (e: FirebaseAuthUserCollisionException) {
        throw AccountException("An account with this email already exists. Sign in instead.")
    } catch (e: FirebaseAuthInvalidUserException) {
        throw AccountException("No account found for this email.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw AccountException("The email or password is incorrect.")
    } catch (e: FirebaseNetworkException) {
        throw AccountException("No internet connection. Try again.")
    } catch (e: Exception) {
        throw AccountException(e.message ?: "Something went wrong. Try again.")
    }
}
