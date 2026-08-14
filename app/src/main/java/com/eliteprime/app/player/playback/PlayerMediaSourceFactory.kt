package com.eliteprime.app.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.eliteprime.app.domain.model.StreamInfo
import com.eliteprime.app.domain.model.StreamType

@UnstableApi
class PlayerMediaSourceFactory(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory
) {
    private val TS_SUBTITLE_FORMATS: List<Format> = listOf(
        Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build(),
        Format.Builder()
            .setSampleMimeType(MimeTypes.APPLICATION_CEA708)
            .setCodecs("cea-708")
            .setAccessibilityChannel(1)
            .build()
    )

    fun create(
        streamInfo: StreamInfo,
        resolvedStreamType: ResolvedStreamType,
        retryPolicy: LoadErrorHandlingPolicy
    ): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamInfo.url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(streamInfo.name)
                    .build()
            )
            .build()

        // ALIGNED WITH ORIGINAL APP FLAGS + MEDIA3 1.11.0 MEMORY FIXES
        val extractorsFactory = if (resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE) {
            DefaultExtractorsFactory()
                .setTsExtractorMode(TsExtractor.MODE_SINGLE_PMT)
                .setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                        or DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                        or DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                )
                .setTsSubtitleFormats(TS_SUBTITLE_FORMATS)
                .setDisableArtworkMetadata(true) // MEDIA3 1.11.0: REDUCE RAM FOOTPRINT
        } else {
            DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setDisableArtworkMetadata(true) // MEDIA3 1.11.0: REDUCE RAM FOOTPRINT
        }

        return when (resolvedStreamType) {
            ResolvedStreamType.HLS -> HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)

            ResolvedStreamType.DASH -> DashMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)

            ResolvedStreamType.SMOOTH_STREAMING -> SsMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)

            ResolvedStreamType.RTSP -> RtspMediaSource.Factory()
                .createMediaSource(mediaItem)

            else -> ProgressiveMediaSource.Factory(
                dataSourceFactory,
                extractorsFactory
            )
                .setLoadErrorHandlingPolicy(retryPolicy)
                .createMediaSource(mediaItem)
        }
    }
}
