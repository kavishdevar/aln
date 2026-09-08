//! Wires the AirPods microphone stream to a PipeWire source: AACP access units
//! are decoded and published without touching the playback profile.

use super::decoder::EldDecoder;
use super::source::MicSource;
use crate::bluetooth::aacp::AACPManager;
use log::{error, info, warn};
use std::sync::Arc;
use tokio::task::JoinHandle;

/// Consecutive decode failures tolerated before giving up on the stream.
const MAX_DECODE_ERRORS: u32 = 50;

pub struct MicStream {
    manager: AACPManager,
    task: JoinHandle<()>,
}

impl MicStream {
    /// Starts streaming. Playback stays on A2DP for the whole session.
    pub async fn start(manager: AACPManager) -> Result<Self, String> {
        let mut decoder = EldDecoder::new().map_err(|e| format!("AAC-ELD decoder: {e}"))?;
        let source = Arc::new(
            MicSource::start(decoder.sample_rate(), decoder.channels())
                .map_err(|e| format!("PipeWire source: {e}"))?,
        );

        let mut units = manager
            .start_audio_stream()
            .await
            .map_err(|e| format!("start request: {e}"))?;

        let task = tokio::spawn(async move {
            let mut errors = 0u32;
            let mut frames = 0u64;
            while let Some(unit) = units.recv().await {
                match decoder.decode(&unit) {
                    Ok(pcm) => {
                        source.push(pcm);
                        frames += 1;
                        errors = 0;
                    }
                    Err(e) => {
                        errors += 1;
                        if errors >= MAX_DECODE_ERRORS {
                            error!("Too many AAC-ELD decode failures ({e}), stopping");
                            break;
                        }
                        warn!("AAC-ELD decode failed: {e}");
                    }
                }
            }
            info!("Microphone stream ended after {frames} frames");
        });

        Ok(Self { manager, task })
    }

    /// Stops the stream. Skipping this leaves the buds streaming, which makes
    /// the audio stack fall back to HFP and degrades playback.
    pub async fn stop(self) {
        self.task.abort();
        if let Err(e) = self.manager.stop_audio_stream().await {
            warn!("Could not stop the microphone stream cleanly: {e}");
        }
    }
}
