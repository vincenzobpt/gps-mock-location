package com.mocklocation.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mocklocation.app.BuildConfig
import com.mocklocation.app.ui.theme.Cockpit

private const val GITHUB_URL = "https://github.com/vincenzobpt/gps-mock-location"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(26.dp),
            color = Cockpit.Panel,
            borderColor = Cockpit.HairlineStrong,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Cockpit.Live.copy(alpha = 0.28f), Cockpit.Violet.copy(alpha = 0.18f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("◎", fontSize = 30.sp, color = Cockpit.Live)
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "GPS Mock Location",
                    color = Cockpit.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME} · SDK ${Build.VERSION.SDK_INT}",
                    color = Cockpit.InkFaint,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Injects synthetic fixes into the platform test providers " +
                        "with realistic speed, heading and terrain along a real road route.",
                    color = Cockpit.InkMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(18.dp))
                SetupSteps()

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Cockpit.Hairline,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("SOURCE", color = Cockpit.InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        color = Cockpit.Live.copy(alpha = 0.18f),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("CLOSE", color = Cockpit.Live, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** The three-step setup users actually get wrong, spelled out. */
@Composable
private fun SetupSteps() {
    val steps = listOf(
        "Enable Developer options on the device",
        "Developer options → Select mock location app → this app",
        "Grant precise location when prompted",
    )
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Cockpit.Live.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Cockpit.Live,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = step,
                    color = Cockpit.InkMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
