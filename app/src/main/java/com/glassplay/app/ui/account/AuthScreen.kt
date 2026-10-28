package com.glassplay.app.ui.account

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glassplay.app.Graph
import com.glassplay.app.account.AccountException
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassSegmented
import com.glassplay.app.ui.components.GlassTextField
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.noRippleClick
import com.glassplay.app.ui.theme.AuroraBackground
import com.glassplay.app.ui.theme.Danger
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.glass
import kotlinx.coroutines.launch

/** Sign in or create an account; shown until someone is signed in. */
@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                action()
            } catch (e: AccountException) {
                error = e.message?.takeIf { it.isNotBlank() }
            } finally {
                busy = false
            }
        }
    }

    fun submit() {
        when {
            creating && firstName.isBlank() -> error = "Enter your first name."
            email.isBlank() || !email.contains('@') -> error = "Enter your email address."
            password.length < 6 -> error = "Passwords have at least 6 characters."
            creating -> run { Graph.accounts.signUp(firstName, lastName, email, password) }
            else -> run { Graph.accounts.signIn(email, password) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text("GlassPlay", style = GlassType.largeTitle, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "Sign in to keep your watch history and profile with you.",
                style = GlassType.subhead,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(28.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassSegmented(
                    options = listOf("Sign in", "Create account"),
                    selected = if (creating) 1 else 0,
                    onSelect = { creating = it == 1; error = null; notice = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(creating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            placeholder = "First name",
                            leading = Icons.Rounded.Person,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.weight(1f),
                        )
                        GlassTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            placeholder = "Last name",
                            imeAction = ImeAction.Next,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                GlassTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email",
                    leading = Icons.Rounded.Email,
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    leading = Icons.Rounded.Lock,
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Password,
                    secret = true,
                    onSubmit = { submit() },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, style = GlassType.footnote, color = Danger) }
                notice?.let { Text(it, style = GlassType.footnote, color = TextSecondary) }

                if (busy) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Spinner() }
                } else {
                    GlassButton(
                        if (creating) "Create account" else "Sign in",
                        onClick = { submit() },
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassButton(
                        "Continue with Google",
                        icon = Icons.Rounded.AccountCircle,
                        onClick = { run { Graph.accounts.signInWithGoogle(context as Activity) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!creating) {
                    Text(
                        "Forgot password?",
                        style = GlassType.footnote,
                        color = TextSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 2.dp)
                            .noRippleClick {
                                if (email.isBlank() || !email.contains('@')) {
                                    error = "Enter your email above, then tap Forgot password."
                                } else {
                                    run {
                                        Graph.accounts.resetPassword(email)
                                        notice = "We've emailed you a link to reset your password."
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}
