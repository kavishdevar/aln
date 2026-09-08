//! AAC-ELD decoding for the AirPods microphone stream.

use super::eld::{AUDIO_SPECIFIC_CONFIG, PRESENTATION_SAMPLE_RATE};
use fdk_aac::dec::{Decoder, DecoderError, Transport};

/// Largest PCM frame the decoder is allowed to produce, in samples.
const PCM_CAPACITY: usize = 4096;

pub struct EldDecoder {
    decoder: Decoder,
    pcm: Vec<i16>,
}

impl EldDecoder {
    pub fn new() -> Result<Self, DecoderError> {
        let mut decoder = Decoder::new(Transport::Raw);
        // The buds send raw access units, so the decoder is configured from the
        // AudioSpecificConfig rather than from in-band headers.
        decoder.config_raw(&AUDIO_SPECIFIC_CONFIG)?;
        Ok(Self {
            decoder,
            pcm: vec![0i16; PCM_CAPACITY],
        })
    }

    /// Decodes one access unit, returning the interleaved PCM samples it produced.
    pub fn decode(&mut self, unit: &[u8]) -> Result<&[i16], DecoderError> {
        self.decoder.fill(unit)?;
        self.decoder.decode_frame(&mut self.pcm)?;

        let info = self.decoder.stream_info();
        let samples = (info.frameSize as usize) * (info.numChannels as usize);
        Ok(&self.pcm[..samples.min(self.pcm.len())])
    }

    pub fn channels(&self) -> u32 {
        self.decoder.stream_info().numChannels.max(1) as u32
    }

    /// Rate the samples should be played back at - see PRESENTATION_SAMPLE_RATE.
    pub fn sample_rate(&self) -> u32 {
        PRESENTATION_SAMPLE_RATE
    }
}
