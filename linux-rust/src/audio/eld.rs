//! AirPods stream their microphone as AAC-ELD inside AACP packets, which lets
//! playback stay on A2DP instead of dropping to HFP call quality.

/// AACP opcode carrying microphone data.
pub const AUDIO_STREAM_OPCODE: u8 = 0x58;

/// Bytes before the first access unit in a microphone SDU.
const SDU_HEADER_SIZE: usize = 22;

/// Asks the buds to start sending microphone audio over AACP.
pub const START_AUDIO_STREAM: [u8; 19] = [
    0x04, 0x00, 0x04, 0x00, 0x58, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x01, 0x82, 0x00, 0x00, 0x00,
    0x04, 0x96, 0x00,
];

/// Stops the stream. Must be sent when done: buds left streaming keep the audio
/// stack in a state where PipeWire falls back to HFP and playback degrades.
pub const STOP_AUDIO_STREAM: [u8; 12] = [
    0x04, 0x00, 0x04, 0x00, 0x58, 0x00, 0x00, 0x00, 0x02, 0x00, 0x03, 0x01,
];

/// AudioSpecificConfig describing the stream: AAC-ELD, 48 kHz coding rate.
pub const AUDIO_SPECIFIC_CONFIG: [u8; 4] = [0xF8, 0xE6, 0x30, 0x00];

/// Rate the decoded PCM is presented at. The coding rate in the config above is
/// 48 kHz, but the buds pace frames for 64 kHz playback, so decoding at the
/// coding rate stretches the audio.
pub const PRESENTATION_SAMPLE_RATE: u32 = 64_000;

/// True when the packet is a microphone SDU rather than a control message.
pub fn is_audio_packet(packet: &[u8]) -> bool {
    packet.len() >= 8
        && packet[0] == 0x04
        && packet[2] == 0x04
        && packet[4] == AUDIO_STREAM_OPCODE
        && packet[5] == 0x00
        && packet[6] == 0x01
        && packet[7] == 0x00
}

/// Splits a microphone SDU into access units, stopping at the first truncated
/// one rather than reading past the packet.
pub fn access_units(packet: &[u8]) -> Vec<&[u8]> {
    if !is_audio_packet(packet) || packet.len() < SDU_HEADER_SIZE {
        return Vec::new();
    }

    let mut units = Vec::new();
    let mut offset = SDU_HEADER_SIZE;
    while offset + 5 <= packet.len() {
        // 4 bytes little-endian timestamp, then one length byte.
        let length = packet[offset + 4] as usize;
        let start = offset + 5;
        let Some(end) = start.checked_add(length) else {
            break;
        };
        if end > packet.len() {
            break;
        }
        units.push(&packet[start..end]);
        offset = end;
    }
    units
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sdu(payload: &[u8]) -> Vec<u8> {
        let mut p = vec![0u8; SDU_HEADER_SIZE];
        p[0] = 0x04;
        p[2] = 0x04;
        p[4] = AUDIO_STREAM_OPCODE;
        p[6] = 0x01;
        p.extend_from_slice(payload);
        p
    }

    #[test]
    fn rejects_control_packets() {
        assert!(!is_audio_packet(&[0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x02]));
    }

    #[test]
    fn splits_consecutive_units() {
        let packet = sdu(&[1, 0, 0, 0, 3, 0x11, 0x22, 0x33, 2, 0, 0, 0, 2, 0x44, 0x55]);
        let units = access_units(&packet);
        assert_eq!(units, vec![&[0x11u8, 0x22, 0x33][..], &[0x44u8, 0x55][..]]);
    }

    #[test]
    fn stops_at_truncated_unit() {
        let packet = sdu(&[1, 0, 0, 0, 3, 0x11, 0x22, 0x33, 2, 0, 0, 0, 9, 0x44]);
        assert_eq!(access_units(&packet), vec![&[0x11u8, 0x22, 0x33][..]]);
    }
}
