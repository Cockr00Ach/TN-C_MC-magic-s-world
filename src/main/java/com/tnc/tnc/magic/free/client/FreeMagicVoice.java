package com.tnc.tnc.magic.free.client;

import com.google.gson.JsonParser;
import com.tnc.tnc.magic.free.ElementalIntent;
import com.tnc.tnc.magic.free.FreeMagicIntent;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.free.EquippedSpellVoice;
import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Captures a held utterance into a local recognizer process. Neither writes nor sends audio. */
public final class FreeMagicVoice {
    public enum Kind { READY, PARTIAL, FINAL, ERROR }
    public record Update(Kind kind, String text, String shapeHint) {
        public Update(Kind kind, String text) {
            this(kind, text, "");
        }

        public Update forLoadout(MagicStoneData data) {
            if (kind != Kind.PARTIAL && kind != Kind.FINAL) return this;
            String repaired = EquippedSpellVoice.repairName(text, data);
            return repaired.equals(text) ? this : new Update(kind, repaired, shapeHint);
        }
    }

    private static final String RUNTIME_PROPERTY = "tnc.freeMagicVoiceRuntime";
    private static final int[] SAMPLE_RATES = {16000, 48000, 44100};
    private static final long MAX_RECORDING_NANOS = TimeUnit.SECONDS.toNanos(20);
    private static final Update READY = new Update(Kind.READY, "");
    private static final ScheduledExecutorService IDLE_REAPER =
            Executors.newSingleThreadScheduledExecutor(action -> daemon("TNC voice idle cleanup", action));
    private static long nextUtterance;
    private static Session active;
    private static Engine warm;
    private static long warmedAfter;
    private static ScheduledFuture<?> expiry;
    private static boolean readyPending;
    private static Update partial;
    private static Update terminal;

    private FreeMagicVoice() {}

    // The spotter emits the canonical label even when it heard an alias. The client
    // resolves a recognised label against the wand after reading the whole reply.
    static String validHint(String hint) {
        for (var shape : FreeMagicIntent.Shape.values()) {
            if (shape.label.equals(hint)) return hint;
        }
        for (var ability : ElementalIntent.Ability.values()) {
            if (ability.label.equals(hint)) return hint;
        }
        return "";
    }

    public static synchronized boolean start() {
        if (active != null || terminal != null) return false;
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        String runtimeSetting = System.getProperty(RUNTIME_PROPERTY, "free-magic-voice");
        active = new Session(++nextUtterance, gameDirectory, runtimeSetting);
        background("TNC voice capture", active::run);
        return true;
    }

    public static synchronized void release() {
        if (active != null) active.recording = false;
    }

    public static void cancel() {
        Session current;
        synchronized (FreeMagicVoice.class) {
            readyPending = false;
            partial = null;
            terminal = null;
            current = active;
            if (current != null) {
                current.cancelled = true;
                current.recording = false;
            }
        }
        if (current != null) current.stopEngine();
    }

    public static void close() {
        cancel();
        Engine idle = takeWarm();
        if (idle != null) idle.stop();
    }

    public static synchronized List<Update> drainUpdates() {
        List<Update> result;
        if (terminal != null) result = List.of(terminal);
        else if (readyPending && partial != null) result = List.of(READY, partial);
        else if (readyPending) result = List.of(READY);
        else if (partial != null) result = List.of(partial);
        else result = List.of();
        readyPending = false;
        partial = null;
        terminal = null;
        if (result.isEmpty()) return result;
        var player = Minecraft.getInstance().player;
        if (player == null) return result;
        var data = MagicStone.getOrNull(player);
        return result.stream().map(update -> update.forLoadout(data)).toList();
    }

    private static synchronized void publishReady(Session session) {
        if (active == session && session.recording && !session.cancelled) readyPending = true;
    }

    private static synchronized void publishPartial(Session session, Update update) {
        if (active == session && session.recording && !session.cancelled) {
            partial = update;
        }
    }

    private static synchronized boolean finish(Session session, Update update) {
        if (active != session) return false;
        active = null;
        readyPending = false;
        partial = null;
        if (session.cancelled) return false;
        terminal = update;
        if (update.kind() != Kind.FINAL || session.engine == null || !session.engine.process.isAlive()) return false;
        warm = session.engine;
        Engine expected = warm;
        warmedAfter = session.id;
        expiry = IDLE_REAPER.schedule(() -> expireWarm(expected, session.id), 45, TimeUnit.SECONDS);
        return true;
    }

    private static synchronized Engine takeWarm() {
        if (expiry != null) expiry.cancel(false);
        expiry = null;
        Engine idle = warm;
        warm = null;
        return idle;
    }

    private static void expireWarm(Engine expected, long utterance) {
        synchronized (FreeMagicVoice.class) {
            if (warm != expected || warmedAfter != utterance) return;
            expiry = null;
            warm = null;
        }
        expected.stop();
    }

    private static Thread daemon(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    private static Thread background(String name, Runnable action) {
        Thread thread = daemon(name, action);
        thread.start();
        return thread;
    }

    private static final class Engine {
        private final Path runtime;
        private final Process process;
        private final DataOutputStream input;
        private final BufferedReader output;

        private Engine(Path runtime, Path python) throws IOException {
            this.runtime = runtime;
            process = new ProcessBuilder(python.toString(), "-I", "-u", "recognize.py", "--model-dir", "model")
                    .directory(runtime.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            input = new DataOutputStream(process.getOutputStream());
            output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        }

        private void stop() {
            if (!process.isAlive()) return;
            // On Windows the venv launcher can start a child interpreter.
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private static final class Session {
        private final long id;
        private final Path gameDirectory;
        private final String runtimeSetting;
        private final ArrayBlockingQueue<byte[]> audio = new ArrayBlockingQueue<>(400);
        private final long deadline = System.nanoTime() + MAX_RECORDING_NANOS;
        private volatile boolean recording = true;
        private volatile boolean cancelled;
        private volatile boolean captureDone;
        private volatile Engine engine;
        private volatile Update finalUpdate;
        private volatile boolean pipeFailed;

        private Session(long id, Path gameDirectory, String runtimeSetting) {
            this.id = id;
            this.gameDirectory = gameDirectory;
            this.runtimeSetting = runtimeSetting;
        }

        private void run() {
            Update result = new Update(Kind.ERROR, "本地语音识别启动失败");
            try {
                result = recognize();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException ignored) {
                // Process and device failures must never interrupt the client tick.
            } finally {
                recording = false;
                captureDone = true;
                if (!finish(this, result)) stopEngine();
            }
        }

        private Update recognize() throws IOException, InterruptedException {
            if (!recording) return new Update(Kind.FINAL, "");
            Path runtime = gameDirectory.resolve(runtimeSetting).toAbsolutePath().normalize();
            Path python = runtime.resolve("venv/Scripts/python.exe");
            if (!Files.isRegularFile(python)) python = runtime.resolve("venv/bin/python");
            if (!Files.isRegularFile(python) || !Files.isRegularFile(runtime.resolve("recognize.py"))
                    || !Files.isRegularFile(runtime.resolve("model/model.int8.onnx"))
                    || !Files.isRegularFile(runtime.resolve("model/tokens.txt"))) {
                return new Update(Kind.ERROR, "本地语音识别组件未安装，请安装后重试");
            }

            Thread reader;
            boolean withinTime;
            try (TargetDataLine microphone = openMicrophone()) {
                if (microphone == null) {
                    return new Update(Kind.ERROR, "无法打开麦克风，请检查系统权限与默认输入设备");
                }
                if (!recording) return new Update(Kind.FINAL, "");
                int sampleRate = (int) microphone.getFormat().getSampleRate();
                microphone.start();
                publishReady(this);
                Engine idle = takeWarm();
                if (idle != null && (!idle.runtime.equals(runtime) || !idle.process.isAlive())) {
                    idle.stop();
                    idle = null;
                }
                engine = idle != null ? idle : new Engine(runtime, python);
                if (!recording) return new Update(Kind.FINAL, "");
                reader = background("TNC voice text", this::readText);
                background("TNC voice audio pipe", () -> writeAudio(sampleRate));
                withinTime = capture(microphone, sampleRate);
            } finally {
                captureDone = true;
            }
            if (cancelled) return new Update(Kind.FINAL, "");
            if (!withinTime) return new Update(Kind.ERROR, "吟唱时间过长，请松开后重试");
            reader.join(4000);
            if (reader.isAlive()) return new Update(Kind.ERROR, "本地语音识别超时，请重试");
            if (pipeFailed || finalUpdate == null) {
                return new Update(Kind.ERROR, "本地语音识别失败，请检查语音组件");
            }
            return finalUpdate;
        }

        private static TargetDataLine openMicrophone() {
            for (int rate : SAMPLE_RATES) {
                AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
                TargetDataLine line = null;
                try {
                    line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
                    line.open(format, rate * 2); // Buffer the first syllable while the local helper starts.
                    return line;
                } catch (LineUnavailableException | IllegalArgumentException | SecurityException unavailable) {
                    if (line != null) line.close();
                }
            }
            return null;
        }

        private boolean capture(TargetDataLine microphone, int sampleRate) throws InterruptedException, IOException {
            byte[] frame = new byte[sampleRate * 2 / 25];
            while (recording && !pipeFailed && engine.process.isAlive()) {
                if (System.nanoTime() - deadline >= 0) return false;
                int available = Math.min(microphone.available(), frame.length) & ~1;
                if (available == 0) {
                    Thread.sleep(10);
                    continue;
                }
                int read = microphone.read(frame, 0, available);
                if (read > 0 && recording && !audio.offer(Arrays.copyOf(frame, read))) {
                    throw new IOException("recognizer cannot consume microphone samples");
                }
            }
            return true;
        }

        private void writeAudio(int sampleRate) {
            try {
                var input = engine.input;
                input.writeInt(-sampleRate);
                input.writeLong(id);
                while (!cancelled && (!captureDone || !audio.isEmpty())) {
                    byte[] frame = audio.poll(20, TimeUnit.MILLISECONDS);
                    if (frame == null) continue;
                    input.writeInt(frame.length);
                    input.write(frame);
                    input.flush();
                }
                if (!cancelled) {
                    input.writeInt(0);
                    input.flush();
                }
            } catch (IOException | InterruptedException failed) {
                pipeFailed = true;
            }
        }

        private void readText() {
            try {
                for (String line; !cancelled && (line = engine.output.readLine()) != null;) {
                    var message = JsonParser.parseString(line).getAsJsonObject();
                    if (message.get("id").getAsLong() != id) throw new IOException("stale recognizer reply");
                    String text = message.get("text").getAsString();
                    var rawShape = message.get("shape");
                    String hint = rawShape != null && rawShape.isJsonPrimitive()
                            && rawShape.getAsJsonPrimitive().isString() ? validHint(rawShape.getAsString()) : "";
                    switch (message.get("kind").getAsString()) {
                        case "partial" -> publishPartial(this, new Update(Kind.PARTIAL, text, hint));
                        case "final" -> {
                            finalUpdate = new Update(Kind.FINAL, text, hint);
                            return;
                        }
                        default -> throw new IOException("unrecognized helper output");
                    }
                }
            } catch (IOException | RuntimeException failed) {
                pipeFailed = true;
            }
        }

        private void stopEngine() {
            Engine running = engine;
            if (running != null) running.stop();
        }
    }
}
