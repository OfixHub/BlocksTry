package com.ofixhub.blockstry.tile

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.ActionBuilders
import com.ofixhub.blockstry.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"

class MainTileService : TileService() {

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    "ic_launcher",
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.mipmap.ic_launcher)
                                .build()
                        )
                        .build()
                )
                .addIdToImageMapping(
                    "tile_background",
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.mipmap.ic_launcher_round)
                                .build()
                        )
                        .build()
                )
                .build()
        )
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(
                                tileLayout(this)
                            ).build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTimeline(singleTileTimeline)
                .build()
        )
    }
}

private fun tileLayout(context: Context): LayoutElementBuilders.LayoutElement {
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setModifiers(
             ModifiersBuilders.Modifiers.Builder()
                .setClickable(
                    ModifiersBuilders.Clickable.Builder()
                        .setId("launch_app")
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setClassName("com.ofixhub.blockstry.GameActivity")
                                        .setPackageName("com.ofixhub.blockstry")
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )
        // Background Image
        .addContent(
             LayoutElementBuilders.Image.Builder()
                .setResourceId("tile_background")
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
                .build()
        )
        // Overlay for readability
        .addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(
                                    ColorBuilders.ColorProp.Builder()
                                        .setArgb(Color.Black.copy(alpha = 0.6f).toArgb())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )
        // Content
        .addContent(
            LayoutElementBuilders.Column.Builder()
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId("ic_launcher")
                        .setWidth(DimensionBuilders.dp(48f))
                        .setHeight(DimensionBuilders.dp(48f))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Spacer.Builder()
                        .setHeight(DimensionBuilders.dp(8f))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("Jugar")
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                            .setColor(ColorBuilders.ColorProp.Builder()
                                .setArgb(Color.White.toArgb())
                                .build())
                            .build())
                        .build()
                )
                .build()
        )
        .build()
}
