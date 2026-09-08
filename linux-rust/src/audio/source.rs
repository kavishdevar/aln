//! Publishes decoded AirPods microphone audio as a PipeWire source, so any
//! application can record from it while playback stays on A2DP.

use log::{error, info};
use pipewire as pw;
use pw::properties::properties;
use pw::spa;
use spa::pod::Pod;
use std::collections::VecDeque;
use std::sync::{Arc, Mutex};

const SAMPLE_SIZE: usize = std::mem::size_of::<i16>();

/// Upper bound on buffered audio. Anything older is dropped rather than played
/// late: a microphone that lags behind is worse than one that skips.
const MAX_QUEUED_SAMPLES: usize = 64_000 / 2; // ~0.5 s at the presentation rate

enum Command {
    Quit,
}

pub struct MicSource {
    samples: Arc<Mutex<VecDeque<i16>>>,
    sender: pw::channel::Sender<Command>,
    thread: Option<std::thread::JoinHandle<()>>,
}

impl MicSource {
    /// Starts the PipeWire node on its own thread.
    pub fn start(rate: u32, channels: u32) -> std::io::Result<Self> {
        let samples = Arc::new(Mutex::new(VecDeque::new()));
        let (sender, receiver) = pw::channel::channel();

        let queue = Arc::clone(&samples);
        let thread = std::thread::Builder::new()
            .name("librepods-mic".into())
            .spawn(move || {
                if let Err(e) = run(queue, receiver, rate, channels) {
                    error!("Microphone source stopped: {e}");
                }
            })?;

        Ok(Self {
            samples,
            sender,
            thread: Some(thread),
        })
    }

    /// Queues decoded samples for delivery to whoever is recording.
    pub fn push(&self, pcm: &[i16]) {
        let Ok(mut queue) = self.samples.lock() else {
            return;
        };
        queue.extend(pcm.iter().copied());
        if queue.len() > MAX_QUEUED_SAMPLES {
            // Nothing is recording, or the reader fell behind. Dropping the
            // oldest audio keeps latency bounded; logging every overflow would
            // flood the log at 130 frames a second.
            let excess = queue.len() - MAX_QUEUED_SAMPLES;
            queue.drain(..excess);
        }
    }
}

impl Drop for MicSource {
    fn drop(&mut self) {
        let _ = self.sender.send(Command::Quit);
        if let Some(thread) = self.thread.take() {
            let _ = thread.join();
        }
        info!("Microphone source removed");
    }
}

fn run(
    samples: Arc<Mutex<VecDeque<i16>>>,
    receiver: pw::channel::Receiver<Command>,
    rate: u32,
    channels: u32,
) -> Result<(), pw::Error> {
    pw::init();

    let mainloop = pw::main_loop::MainLoopRc::new(None)?;
    let context = pw::context::ContextRc::new(&mainloop, None)?;
    let core = context.connect_rc(None)?;

    let stream = pw::stream::StreamBox::new(
        &core,
        "librepods-mic",
        properties! {
            *pw::keys::MEDIA_TYPE => "Audio",
            *pw::keys::MEDIA_CATEGORY => "Capture",
            // Audio/Source is what makes this node show up as a microphone.
            *pw::keys::MEDIA_CLASS => "Audio/Source",
            *pw::keys::NODE_NAME => "librepods_mic",
            *pw::keys::NODE_DESCRIPTION => "AirPods Microphone (LibrePods)",
        },
    )?;

    let stride = SAMPLE_SIZE * channels as usize;
    let _listener = stream
        .add_local_listener_with_user_data(samples)
        .process(move |stream, samples| {
            let Some(mut buffer) = stream.dequeue_buffer() else {
                return;
            };
            let data = &mut buffer.datas_mut()[0];
            let Some(slice) = data.data() else {
                return;
            };

            let wanted = slice.len() / SAMPLE_SIZE;
            let mut written = 0;
            if let Ok(mut queue) = samples.lock() {
                while written < wanted {
                    let Some(sample) = queue.pop_front() else {
                        break;
                    };
                    let start = written * SAMPLE_SIZE;
                    slice[start..start + SAMPLE_SIZE].copy_from_slice(&sample.to_le_bytes());
                    written += 1;
                }
            }
            // Silence rather than stale audio when the buds fall behind.
            for i in written..wanted {
                let start = i * SAMPLE_SIZE;
                slice[start..start + SAMPLE_SIZE].copy_from_slice(&0i16.to_le_bytes());
            }

            let chunk = data.chunk_mut();
            *chunk.offset_mut() = 0;
            *chunk.stride_mut() = stride as _;
            *chunk.size_mut() = (wanted * SAMPLE_SIZE) as _;
        })
        .register()?;

    let mut audio_info = spa::param::audio::AudioInfoRaw::new();
    audio_info.set_format(spa::param::audio::AudioFormat::S16LE);
    audio_info.set_rate(rate);
    audio_info.set_channels(channels);

    let values: Vec<u8> = pw::spa::pod::serialize::PodSerializer::serialize(
        std::io::Cursor::new(Vec::new()),
        &pw::spa::pod::Value::Object(pw::spa::pod::Object {
            type_: pw::spa::sys::SPA_TYPE_OBJECT_Format,
            id: pw::spa::sys::SPA_PARAM_EnumFormat,
            properties: audio_info.into(),
        }),
    )
    .map_err(|_| pw::Error::CreationFailed)?
    .0
    .into_inner();
    let mut params = [Pod::from_bytes(&values).ok_or(pw::Error::CreationFailed)?];

    stream.connect(
        spa::utils::Direction::Output,
        None,
        pw::stream::StreamFlags::AUTOCONNECT
            | pw::stream::StreamFlags::MAP_BUFFERS
            | pw::stream::StreamFlags::RT_PROCESS,
        &mut params,
    )?;

    let _receiver = receiver.attach(mainloop.loop_(), {
        let mainloop = mainloop.clone();
        move |_| mainloop.quit()
    });

    info!("Microphone source published as \"AirPods Microphone (LibrePods)\" at {rate} Hz");
    mainloop.run();
    Ok(())
}
