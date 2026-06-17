package com.rpeters.jellyfin.ui.surface.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.Color
import com.rpeters.jellyfin.R

class RecentlyAddedWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            RecentlyAddedContent()
        }
    }

    @Composable
    private fun RecentlyAddedContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "Recently Added",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = androidx.glance.unit.ColorProvider(Color.White)
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    text = "New movies and shows will appear here",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = androidx.glance.unit.ColorProvider(Color(0xFF888888))
                    ),
                    modifier = GlanceModifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

class RecentlyAddedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentlyAddedWidget()
}
